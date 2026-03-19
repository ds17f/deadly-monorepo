"use client";

import { useState, useRef, useCallback, useEffect, useMemo } from "react";
import type { ArchiveTrack, PlaybackStatus } from "@/types/player";
import { fetchArchiveTracks } from "@/lib/archive";
import { updatePlaybackPosition } from "@/lib/userDataApi";
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
  const { announcePlayback, sendPositionUpdate } = useConnect();

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

  // Dual audio elements for gapless playback
  const audioARef = useRef<HTMLAudioElement | null>(null);
  const audioBRef = useRef<HTMLAudioElement | null>(null);
  const activeAudioRef = useRef<"A" | "B">("A");
  const retryCountRef = useRef(0);
  const tracksRef = useRef<ArchiveTrack[] | null>(null);
  const currentTrackIndexRef = useRef(-1);
  const preloadedNextRef = useRef(false);

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

    audio.addEventListener("playing", () => {
      if (
        (activeAudioRef.current === "A" && audio === audioARef.current) ||
        (activeAudioRef.current === "B" && audio === audioBRef.current)
      ) {
        setStatus("playing");
        retryCountRef.current = 0;
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
    setStatus("loading");
    audio.src = track.url;
    audio.play().catch(() => {
      setStatus("paused");
    });

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

  // Report playback position every 15s while playing, and on pause/track change
  const positionReportRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (positionReportRef.current) {
      clearInterval(positionReportRef.current);
      positionReportRef.current = null;
    }

    function reportPosition() {
      const audio = getActiveAudio();
      if (!activeShow || !selectedRecording || !audio || currentTrackIndex < 0) return;
      const positionMs = Math.floor(audio.currentTime * 1000);
      // Report via REST (fallback/persistence)
      updatePlaybackPosition({
        showId: activeShow.showId,
        recordingId: selectedRecording,
        trackIndex: currentTrackIndex,
        positionMs,
      }).catch(() => {});
      // Also report via WebSocket for real-time sync
      sendPositionUpdate({
        showId: activeShow.showId,
        recordingId: selectedRecording,
        trackIndex: currentTrackIndex,
        positionMs,
        status: status === "playing" ? "playing" : "paused",
      });
    }

    if (status === "playing") {
      positionReportRef.current = setInterval(reportPosition, 15000);
    } else if (status === "paused") {
      // Report once on pause
      reportPosition();
    }

    return () => {
      if (positionReportRef.current) {
        clearInterval(positionReportRef.current);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, activeShow, selectedRecording, currentTrackIndex, sendPositionUpdate]);

  // Announce playback state changes via WebSocket for Connect session
  useEffect(() => {
    if (!activeShow || !selectedRecording || currentTrackIndex < 0) return;
    if (status !== "playing" && status !== "paused") return;

    const audio = getActiveAudio();
    const positionMs = audio ? Math.floor(audio.currentTime * 1000) : 0;

    announcePlayback({
      showId: activeShow.showId,
      recordingId: selectedRecording,
      trackIndex: currentTrackIndex,
      positionMs,
      status: status === "playing" ? "playing" : "paused",
      date: activeShow.date,
      venue: activeShow.venue,
      location: activeShow.location,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, activeShow, selectedRecording, currentTrackIndex, announcePlayback]);

  // Listen for absolute seek from Connect transfer
  useEffect(() => {
    function handleConnectSeek(e: Event) {
      const { seconds } = (e as CustomEvent).detail;
      const audio = getActiveAudio();
      if (audio && typeof seconds === "number") {
        audio.currentTime = seconds;
      }
    }
    window.addEventListener("connect:seek", handleConnectSeek);
    return () => window.removeEventListener("connect:seek", handleConnectSeek);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function updateMediaSession(track: ArchiveTrack) {
    if (!("mediaSession" in navigator)) return;
    const showId = activeShow?.showId ?? "";
    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: "Grateful Dead",
      album: showId.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()),
    });
  }

  const playRecording = useCallback(async (identifier: string) => {
    setIsLoadingTracks(true);
    setErrorMessage(null);
    try {
      const fetchedTracks = await fetchArchiveTracks(identifier);
      if (fetchedTracks.length === 0) {
        setErrorMessage("No playable audio files found for this recording.");
        setIsLoadingTracks(false);
        return;
      }
      setTracks(fetchedTracks);
      setCurrentTrackIndex(0);
    } catch {
      setErrorMessage("Failed to load tracks from Archive.org.");
    }
    setIsLoadingTracks(false);
  }, []);

  const playShow = useCallback(
    (show: ViewedShow) => {
      setActiveShow(show);
      const recId =
        show.bestRecordingId ?? show.recordings[0]?.identifier ?? null;
      setSelectedRecording(recId);
      if (recId) {
        playRecording(recId);
      }
    },
    [playRecording]
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
    const audio = getActiveAudio();
    if (!audio) return;

    if (audio.paused) {
      audio.play().catch(() => {});
    } else {
      audio.pause();
    }
  }, []);

  const nextTrack = useCallback(() => {
    if (!tracks) return;
    setCurrentTrackIndex((prev) => {
      if (prev < tracks.length - 1) return prev + 1;
      return prev;
    });
  }, [tracks]);

  const prevTrack = useCallback(() => {
    const audio = getActiveAudio();
    if (!audio || !tracks) return;

    if (audio.currentTime > PREV_TRACK_THRESHOLD || currentTrackIndex === 0) {
      audio.currentTime = 0;
    } else {
      setCurrentTrackIndex((prev) => Math.max(0, prev - 1));
    }
  }, [tracks, currentTrackIndex]);

  const seek = useCallback((fraction: number) => {
    const audio = getActiveAudio();
    if (!audio || !isFinite(audio.duration)) return;
    audio.currentTime = fraction * audio.duration;
  }, []);

  const close = useCallback(() => {
    // Clear error first to prevent stale error flash
    setErrorMessage(null);

    // Announce stop before clearing state so the server knows playback ended
    if (activeShow && selectedRecording) {
      const audio = getActiveAudio();
      const positionMs = audio ? Math.floor(audio.currentTime * 1000) : 0;
      announcePlayback({
        showId: activeShow.showId,
        recordingId: selectedRecording,
        trackIndex: currentTrackIndex,
        positionMs,
        status: "stopped",
        date: activeShow.date,
        venue: activeShow.venue,
        location: activeShow.location,
      });
    }

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
    setActiveShow(null);
    setSelectedRecording(null);
    setIsLoadingTracks(false);
    if ("mediaSession" in navigator) {
      navigator.mediaSession.metadata = null;
    }
  }, [activeShow, selectedRecording, currentTrackIndex, announcePlayback]);

  const selectRecording = useCallback(
    (identifier: string) => {
      setSelectedRecording(identifier);
      // If already playing, switch to the new recording
      if (status !== "idle") {
        playRecording(identifier);
      }
    },
    [status, playRecording]
  );

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
      selectRecording,
      playShow,
      playRecording,
      playTrack,
      togglePlayPause,
      nextTrack,
      prevTrack,
      seek,
      close,
    ]
  );

  return (
    <PlayerContext.Provider value={value}>
      {children}
    </PlayerContext.Provider>
  );
}
