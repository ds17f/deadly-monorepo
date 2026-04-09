"use client";

import { useState, useRef, useCallback, useEffect, useMemo } from "react";
import type { ArchiveTrack, PlaybackStatus } from "@/types/player";
import { fetchArchiveTracks } from "@/lib/archive";
import { PlayerContext } from "@/contexts/PlayerContext";
import type { ViewedShow } from "@/contexts/PlayerContext";
import { useConnect } from "@/contexts/ConnectContext";

const PREV_TRACK_THRESHOLD = 3; // seconds
const AUDIO_RETRY_DELAYS = [0, 1000, 2000];
const GAPLESS_PRELOAD_THRESHOLD = 2; // seconds before end to start preloading

export default function PlayerProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [activeShow, setActiveShow] = useState<ViewedShow | null>(null);
  const [viewedShow, setViewedShow] = useState<ViewedShow | null>(null);
  const [tracks, setTracks] = useState<ArchiveTrack[] | null>(null);
  const [currentTrackIndex, setCurrentTrackIndex] = useState(-1);
  const [status, setStatus] = useState<PlaybackStatus>("idle");
  const [elapsed, setElapsed] = useState(0);
  const [duration, setDuration] = useState(0);
  const [selectedRecording, setSelectedRecording] = useState<string | null>(
    null
  );
  const [isLoadingTracks, setIsLoadingTracks] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [autoplayBlocked, setAutoplayBlocked] = useState(false);
  const [pendingCommand, setPendingCommand] = useState<string | null>(null);
  const [pendingTransfer, setPendingTransfer] = useState<string | null>(null);

  const { state: connectState, myDeviceId, sendCommand } = useConnect();

  const isActiveDevice = connectState !== null && myDeviceId !== null && connectState.activeDeviceId === myDeviceId;
  // Show Connect state whenever a shared show is loaded and we're not the active device.
  // Covers both "another device is active" and "paused with no active device".
  const isRemoteControlling = connectState !== null && connectState.showId !== null && !isActiveDevice;

  // Dual audio elements for gapless playback
  const audioARef = useRef<HTMLAudioElement | null>(null);
  const audioBRef = useRef<HTMLAudioElement | null>(null);
  const activeAudioRef = useRef<"A" | "B">("A");
  const retryCountRef = useRef(0);
  const tracksRef = useRef<ArchiveTrack[] | null>(null);
  const currentTrackIndexRef = useRef(-1);
  const preloadedNextRef = useRef(false);
  const pendingSeekMsRef = useRef<number | null>(null);
  const suppressAutoPlayRef = useRef(false);

  // Keep refs in sync
  useEffect(() => {
    tracksRef.current = tracks;
  }, [tracks]);
  useEffect(() => {
    currentTrackIndexRef.current = currentTrackIndex;
  }, [currentTrackIndex]);

  function getActiveAudio(): HTMLAudioElement | null {
    return activeAudioRef.current === "A"
      ? audioARef.current
      : audioBRef.current;
  }

  function getInactiveAudio(): HTMLAudioElement | null {
    return activeAudioRef.current === "A"
      ? audioBRef.current
      : audioARef.current;
  }

  function swapActive() {
    activeAudioRef.current = activeAudioRef.current === "A" ? "B" : "A";
  }

  // Wire up event listeners for an audio element
  function wireAudioEvents(audio: HTMLAudioElement) {
    audio.addEventListener("timeupdate", () => {
      // Only update UI for the active audio element
      if (
        (activeAudioRef.current === "A" && audio === audioARef.current) ||
        (activeAudioRef.current === "B" && audio === audioBRef.current)
      ) {
        setElapsed(audio.currentTime);
        setDuration(audio.duration || 0);

        // Gapless preload: preload next track when within threshold of end
        if (
          audio.duration &&
          isFinite(audio.duration) &&
          audio.duration - audio.currentTime <= GAPLESS_PRELOAD_THRESHOLD &&
          !preloadedNextRef.current
        ) {
          const t = tracksRef.current;
          const idx = currentTrackIndexRef.current;
          if (t && idx >= 0 && idx < t.length - 1) {
            const nextTrack = t[idx + 1];
            const inactive = getInactiveAudio();
            if (inactive) {
              inactive.src = nextTrack.url;
              inactive.preload = "auto";
              preloadedNextRef.current = true;
            }
          }
        }
      }
    });

    audio.addEventListener("loadedmetadata", () => {
      if (
        (activeAudioRef.current === "A" && audio === audioARef.current) ||
        (activeAudioRef.current === "B" && audio === audioBRef.current)
      ) {
        if (pendingSeekMsRef.current !== null) {
          audio.currentTime = pendingSeekMsRef.current / 1000;
          pendingSeekMsRef.current = null;
        }
      }
    });

    audio.addEventListener("playing", () => {
      if (
        (activeAudioRef.current === "A" && audio === audioARef.current) ||
        (activeAudioRef.current === "B" && audio === audioBRef.current)
      ) {
        setStatus("playing");
        retryCountRef.current = 0;
        setAutoplayBlocked(false);
      }
    });

    audio.addEventListener("pause", () => {
      if (
        (activeAudioRef.current === "A" && audio === audioARef.current) ||
        (activeAudioRef.current === "B" && audio === audioBRef.current)
      ) {
        if (!audio.seeking) {
          setStatus((prev) => (prev === "loading" ? prev : "paused"));
        }
      }
    });

    audio.addEventListener("waiting", () => {
      if (
        (activeAudioRef.current === "A" && audio === audioARef.current) ||
        (activeAudioRef.current === "B" && audio === audioBRef.current)
      ) {
        setStatus("buffering");
      }
    });

    audio.addEventListener("ended", () => {
      if (
        (activeAudioRef.current === "A" && audio === audioARef.current) ||
        (activeAudioRef.current === "B" && audio === audioBRef.current)
      ) {
        const t = tracksRef.current;
        const idx = currentTrackIndexRef.current;
        if (t && idx < t.length - 1) {
          // Gapless: if next track is preloaded in inactive element, start it immediately
          const inactive = getInactiveAudio();
          if (inactive && preloadedNextRef.current && inactive.src) {
            inactive.play().catch(() => {});
            swapActive();
            // Clear the now-inactive element (the one that just ended)
            audio.src = "";
            audio.removeAttribute("src");
          }
          preloadedNextRef.current = false;
          setCurrentTrackIndex(idx + 1);
        } else {
          setStatus("paused");
        }
      }
    });

    audio.addEventListener("error", () => {
      if (
        (activeAudioRef.current === "A" && audio === audioARef.current) ||
        (activeAudioRef.current === "B" && audio === audioBRef.current)
      ) {
        const retryIndex = retryCountRef.current;
        if (retryIndex < AUDIO_RETRY_DELAYS.length) {
          const delay = AUDIO_RETRY_DELAYS[retryIndex];
          retryCountRef.current = retryIndex + 1;
          setTimeout(() => {
            audio.load();
            audio.play().catch(() => {});
          }, delay);
        } else {
          setStatus("error");
          setErrorMessage(
            "This track could not be loaded. Try another recording or listen on Archive.org."
          );
        }
      }
    });
  }

  // Create audio elements once
  useEffect(() => {
    const audioA = new Audio();
    audioA.preload = "auto";
    audioARef.current = audioA;

    const audioB = new Audio();
    audioB.preload = "none";
    audioBRef.current = audioB;

    wireAudioEvents(audioA);
    wireAudioEvents(audioB);

    return () => {
      audioA.pause();
      audioA.src = "";
      audioB.pause();
      audioB.src = "";
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // When currentTrackIndex changes, load and play the track
  // (unless gapless already started it via the ended handler)
  useEffect(() => {
    const audio = getActiveAudio();
    if (
      !audio ||
      !tracks ||
      currentTrackIndex < 0 ||
      currentTrackIndex >= tracks.length
    )
      return;

    const track = tracks[currentTrackIndex];

    // If the active audio already has this track's URL loaded (gapless swap), skip re-loading
    if (audio.src === track.url && !audio.paused) {
      updateMediaSession(track);
      return;
    }

    retryCountRef.current = 0;
    preloadedNextRef.current = false;
    setErrorMessage(null);
    audio.src = track.url;

    if (suppressAutoPlayRef.current) {
      // Hydrating for a non-active device — preload only, don't play
      audio.preload = "auto";
      audio.load();
      suppressAutoPlayRef.current = false;
      setStatus("paused");
    } else {
      setStatus("loading");
      audio.play().catch((err) => {
        if (err instanceof DOMException && err.name === "NotAllowedError") {
          setAutoplayBlocked(true);
        }
        setStatus("paused");
      });
    }

    updateMediaSession(track);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentTrackIndex, tracks]);

  // Register MediaSession action handlers
  useEffect(() => {
    if (!("mediaSession" in navigator)) return;

    navigator.mediaSession.setActionHandler("play", () => togglePlayPause());
    navigator.mediaSession.setActionHandler("pause", () => togglePlayPause());
    navigator.mediaSession.setActionHandler("previoustrack", () => prevTrack());
    navigator.mediaSession.setActionHandler("nexttrack", () => nextTrack());
    navigator.mediaSession.setActionHandler("seekto", (details) => {
      const audio = getActiveAudio();
      if (audio && details.seekTime != null) {
        audio.currentTime = details.seekTime;
      }
    });

    return () => {
      navigator.mediaSession.setActionHandler("play", null);
      navigator.mediaSession.setActionHandler("pause", null);
      navigator.mediaSession.setActionHandler("previoustrack", null);
      navigator.mediaSession.setActionHandler("nexttrack", null);
      navigator.mediaSession.setActionHandler("seekto", null);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);


  // React to ConnectState broadcasts
  useEffect(() => {
    if (!connectState) return;

    // Clear pending command if server confirmed the expected state
    setPendingCommand((prev) => {
      if (prev === "play" && connectState.playing) return null;
      if (prev === "pause" && !connectState.playing) return null;
      if (prev === "next" || prev === "prev" || prev === "seek") return null;
      return prev;
    });

    // Clear pending transfer when a device becomes active (transfer resolved)
    setPendingTransfer((prev) => {
      if (!prev) return null;
      if (connectState.activeDeviceId) return null;
      return prev;
    });

    // Hydrate local audio when the server recording changes (or first state).
    // All connected clients stay loaded so transfers are instant.
    if (connectState.recordingId && connectState.recordingId !== selectedRecording) {
      const autoPlay = isActiveDevice && connectState.playing;
      pendingSeekMsRef.current = connectState.positionMs;
      if (!autoPlay) suppressAutoPlayRef.current = true;
      setActiveShow({
        showId: connectState.showId ?? "",
        recordings: [],
        bestRecordingId: connectState.recordingId,
        date: connectState.date ?? "",
        venue: connectState.venue ?? "",
        location: connectState.location ?? "",
      });
      setSelectedRecording(connectState.recordingId);
      playRecording(connectState.recordingId).then((fetchedTracks) => {
        if (fetchedTracks.length > 0) {
          const targetIndex = connectState.trackIndex < fetchedTracks.length
            ? connectState.trackIndex : 0;
          setCurrentTrackIndex(targetIndex);
        }
      });
      return; // skip play/pause sync until tracks are loaded
    }

    // If another device is now active, pause our local audio and report final position
    if (isRemoteControlling) {
      const audio = getActiveAudio();
      if (audio && !audio.paused) {
        const positionMs = Math.round(audio.currentTime * 1000);
        audio.pause();
        sendCommand("position", { positionMs });
      }
    }

    // If we ARE the active device, sync local audio to server state
    if (isActiveDevice) {
      const audio = getActiveAudio();
      if (audio) {
        // Sync track index if server changed it (remote next/prev)
        if (connectState.trackIndex !== currentTrackIndexRef.current) {
          setCurrentTrackIndex(connectState.trackIndex);
        }
        // Sync position if server changed it (remote seek)
        const serverPositionS = connectState.positionMs / 1000;
        if (Math.abs(audio.currentTime - serverPositionS) > 1) {
          audio.currentTime = serverPositionS;
        }
        if (connectState.playing && audio.paused) {
          audio.play().catch(() => {});
        } else if (!connectState.playing && !audio.paused) {
          audio.pause();
        }
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectState?.version]);

  function updateMediaSession(track: ArchiveTrack) {
    if (!("mediaSession" in navigator)) return;
    const showId = activeShow?.showId ?? "";
    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: "Grateful Dead",
      album: showId.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()),
    });
  }

  const playRecording = useCallback(async (identifier: string): Promise<ArchiveTrack[]> => {
    setIsLoadingTracks(true);
    setErrorMessage(null);
    try {
      const fetchedTracks = await fetchArchiveTracks(identifier);
      if (fetchedTracks.length === 0) {
        setErrorMessage("No playable audio files found for this recording.");
        setIsLoadingTracks(false);
        return [];
      }
      setTracks(fetchedTracks);
      setCurrentTrackIndex(0);
      setIsLoadingTracks(false);
      return fetchedTracks;
    } catch {
      setErrorMessage("Failed to load tracks from Archive.org.");
      setIsLoadingTracks(false);
      return [];
    }
  }, []);

  const playShow = useCallback(
    async (show: ViewedShow) => {
      setActiveShow(show);
      const recId =
        show.bestRecordingId ?? show.recordings[0]?.identifier ?? null;
      setSelectedRecording(recId);
      if (!recId) return;
      const fetchedTracks = await playRecording(recId);
      if (fetchedTracks.length > 0) {
        sendCommand("load", {
          showId: show.showId,
          recordingId: recId,
          tracks: fetchedTracks.map((t) => ({
            title: t.title,
            durationMs: Math.round(t.duration * 1000),
          })),
          trackIndex: 0,
          positionMs: 0,
          durationMs: Math.round((fetchedTracks[0]?.duration ?? 0) * 1000),
          date: show.date,
          venue: show.venue,
          location: show.location,
          autoplay: true,
        });
      }
    },
    [playRecording, sendCommand]
  );

  const playTrack = useCallback((index: number) => {
    // Reset gapless state since user is manually selecting
    preloadedNextRef.current = false;
    const inactive = getInactiveAudio();
    if (inactive) {
      inactive.src = "";
      inactive.removeAttribute("src");
    }
    setCurrentTrackIndex(index);
  }, []);

  const togglePlayPause = useCallback(() => {
    if (isRemoteControlling && connectState) {
      // Remote control: send command only, show spinner until server confirms
      const action = connectState.playing ? "pause" : "play";
      setPendingCommand(action);
      sendCommand(action);
      return;
    }

    const audio = getActiveAudio();
    if (!audio) return;

    if (audio.paused) {
      audio.play().catch(() => {});
      sendCommand("play");
    } else {
      audio.pause();
      sendCommand("pause");
    }
  }, [isRemoteControlling, connectState, sendCommand]);

  const transferTo = useCallback((targetDeviceId: string) => {
    if (!connectState?.showId) return;
    setPendingTransfer(targetDeviceId);
    sendCommand("transfer", { targetDeviceId });
  }, [connectState?.showId, sendCommand]);

  const nextTrack = useCallback(() => {
    if (isRemoteControlling) {
      setPendingCommand("next");
      sendCommand("next");
      return;
    }
    if (!tracks) return;
    setCurrentTrackIndex((prev) => {
      if (prev < tracks.length - 1) return prev + 1;
      return prev;
    });
    sendCommand("next");
  }, [tracks, isRemoteControlling, sendCommand]);

  const prevTrack = useCallback(() => {
    if (isRemoteControlling) {
      setPendingCommand("prev");
      sendCommand("prev");
      return;
    }
    const audio = getActiveAudio();
    if (!audio || !tracks) return;

    if (audio.currentTime > PREV_TRACK_THRESHOLD || currentTrackIndex === 0) {
      audio.currentTime = 0;
    } else {
      setCurrentTrackIndex((prev) => Math.max(0, prev - 1));
    }
    sendCommand("prev");
  }, [tracks, currentTrackIndex, isRemoteControlling, sendCommand]);

  const seek = useCallback((fraction: number) => {
    if (isRemoteControlling && connectState) {
      const positionMs = Math.round(fraction * connectState.durationMs);
      setPendingCommand("seek");
      sendCommand("seek", {
        trackIndex: connectState.trackIndex,
        positionMs,
        durationMs: connectState.durationMs,
      });
      return;
    }
    const audio = getActiveAudio();
    if (!audio || !isFinite(audio.duration)) return;
    const positionMs = Math.round(fraction * audio.duration * 1000);
    audio.currentTime = fraction * audio.duration;
    sendCommand("seek", {
      trackIndex: currentTrackIndex,
      positionMs,
      durationMs: Math.round(audio.duration * 1000),
    });
  }, [isRemoteControlling, connectState, currentTrackIndex, sendCommand]);

  const close = useCallback(() => {
    setErrorMessage(null);

    const audioA = audioARef.current;
    const audioB = audioBRef.current;
    if (audioA) {
      audioA.pause();
      audioA.src = "";
    }
    if (audioB) {
      audioB.pause();
      audioB.src = "";
    }
    activeAudioRef.current = "A";
    preloadedNextRef.current = false;

    setStatus("idle");
    setCurrentTrackIndex(-1);
    setTracks(null);
    setElapsed(0);
    setDuration(0);
    setIsLoadingTracks(false);
    if ("mediaSession" in navigator) {
      navigator.mediaSession.metadata = null;
    }
  }, []);

  const dismiss = useCallback(() => {
    close();
    setActiveShow(null);
    setSelectedRecording(null);
  }, [close]);

  const selectRecording = useCallback(
    (identifier: string) => {
      setSelectedRecording(identifier);
      if (status !== "idle") {
        playRecording(identifier);
      }
    },
    [status, playRecording]
  );

  const retryAutoplay = useCallback(() => {
    const audio = getActiveAudio();
    if (!audio) return;
    audio.play().catch(() => { setStatus("paused"); });
    setAutoplayBlocked(false);
  }, []);

  const dismissAutoplay = useCallback(() => {
    setAutoplayBlocked(false);
  }, []);

  const value = useMemo(
    () => ({
      activeShow,
      viewedShow,
      tracks,
      currentTrackIndex,
      status,
      elapsed,
      duration,
      selectedRecording,
      isLoadingTracks,
      errorMessage,
      isActiveDevice,
      isRemoteControlling,
      pendingCommand,
      pendingTransfer,
      transferTo,
      setViewedShow,
      selectRecording,
      playShow,
      playRecording,
      playTrack,
      togglePlayPause,
      nextTrack,
      prevTrack,
      seek,
      close,
      dismiss,
      autoplayBlocked,
      autoplayInfo: null,
      retryAutoplay,
      dismissAutoplay,
    }),
    [
      activeShow,
      viewedShow,
      tracks,
      currentTrackIndex,
      status,
      elapsed,
      duration,
      selectedRecording,
      isLoadingTracks,
      errorMessage,
      isActiveDevice,
      isRemoteControlling,
      pendingCommand,
      pendingTransfer,
      transferTo,
      selectRecording,
      playShow,
      playRecording,
      playTrack,
      togglePlayPause,
      nextTrack,
      prevTrack,
      seek,
      close,
      dismiss,
      autoplayBlocked,
      retryAutoplay,
      dismissAutoplay,
    ]
  );

  return (
    <PlayerContext.Provider value={value}>
      {children}
    </PlayerContext.Provider>
  );
}
