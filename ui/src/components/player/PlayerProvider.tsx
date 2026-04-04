"use client";

import { useState, useRef, useCallback, useEffect, useMemo } from "react";
import type { ArchiveTrack, PlaybackStatus } from "@/types/player";
import { fetchArchiveTracks } from "@/lib/archive";
import { updatePlaybackPosition } from "@/lib/userDataApi";
import { PlayerContext } from "@/contexts/PlayerContext";
import type { ViewedShow } from "@/contexts/PlayerContext";
import { useConnect } from "@/contexts/ConnectContext";
import type { PlaybackState } from "@/contexts/ConnectContext";

const PREV_TRACK_THRESHOLD = 3; // seconds
const AUDIO_RETRY_DELAYS = [0, 1000, 2000];
const GAPLESS_PRELOAD_THRESHOLD = 2; // seconds before end to start preloading

export default function PlayerProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const { announcePlayback, sendPositionUpdate, clearState, claimSession, userState, isActiveDevice, connectConfig } = useConnect();

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
  const [autoplayInfo, setAutoplayInfo] = useState<{ showDate: string; venue: string; fromDevice: string } | null>(null);
  const autoplayBlockedAudioRef = useRef<HTMLAudioElement | null>(null);
  const pendingPlayOnInfoRef = useRef<{ showDate: string; venue: string; fromDevice: string } | null>(null);

  // Dual audio elements for gapless playback
  const audioARef = useRef<HTMLAudioElement | null>(null);
  const audioBRef = useRef<HTMLAudioElement | null>(null);
  const activeAudioRef = useRef<"A" | "B">("A");
  const retryCountRef = useRef(0);
  const tracksRef = useRef<ArchiveTrack[] | null>(null);
  const currentTrackIndexRef = useRef(-1);
  const preloadedNextRef = useRef(false);
  const hydratedRef = useRef(false);
  const wasActiveRef = useRef(false);
  const activeShowRef = useRef<ViewedShow | null>(null);
  const selectedRecordingRef = useRef<string | null>(null);
  const sendPositionUpdateRef = useRef(sendPositionUpdate);
  const statusRef = useRef<PlaybackStatus>("idle");
  const suppressAutoplayRef = useRef(false);
  const suppressAnnounceRef = useRef(false);
  const ignoringAudioEventsRef = useRef(false);
  const prevUserStateRef = useRef<{ isPlaying: boolean; trackIndex: number; positionMs: number } | null>(null);

  // Keep refs in sync
  useEffect(() => {
    tracksRef.current = tracks;
  }, [tracks]);
  useEffect(() => {
    currentTrackIndexRef.current = currentTrackIndex;
  }, [currentTrackIndex]);
  useEffect(() => {
    activeShowRef.current = activeShow;
  }, [activeShow]);
  useEffect(() => {
    selectedRecordingRef.current = selectedRecording;
  }, [selectedRecording]);
  useEffect(() => {
    sendPositionUpdateRef.current = sendPositionUpdate;
  }, [sendPositionUpdate]);
  useEffect(() => {
    statusRef.current = status;
  }, [status]);

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
      if (ignoringAudioEventsRef.current) return;
      if (
        (activeAudioRef.current === "A" && audio === audioARef.current) ||
        (activeAudioRef.current === "B" && audio === audioBRef.current)
      ) {
        setStatus("playing");
        retryCountRef.current = 0;
        setAutoplayBlocked(false);
        setAutoplayInfo(null);
        autoplayBlockedAudioRef.current = null;
        pendingPlayOnInfoRef.current = null;
      }
    });

    audio.addEventListener("pause", () => {
      if (ignoringAudioEventsRef.current) return;
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
      if (ignoringAudioEventsRef.current) return;
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
      if (ignoringAudioEventsRef.current) return;
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

    // Immediately broadcast position on seek so other clients update their progress bars
    audio.addEventListener("seeked", () => {
      if (
        (activeAudioRef.current === "A" && audio === audioARef.current) ||
        (activeAudioRef.current === "B" && audio === audioBRef.current)
      ) {
        const show = activeShowRef.current;
        const rec = selectedRecordingRef.current;
        const idx = currentTrackIndexRef.current;
        const t = tracksRef.current;
        if (!show || !rec || idx < 0) return;
        const positionMs = Math.floor(audio.currentTime * 1000);
        const trackTitle = t && idx >= 0 && idx < t.length ? t[idx].title : undefined;
        sendPositionUpdateRef.current({
          showId: show.showId,
          recordingId: rec,
          trackIndex: idx,
          positionMs,
          durationMs: Math.floor((audio.duration || 0) * 1000),
          trackTitle,
          status: statusRef.current === "playing" ? "playing" : "paused",
        });
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

    // If a play-on transfer arrived while paused, load but don't play
    if (suppressAutoplayRef.current) {
      suppressAutoplayRef.current = false;
      setStatus("paused");
    } else {
      setStatus("loading");
      audio.play().catch((err) => {
        if (err instanceof DOMException && err.name === "NotAllowedError") {
          setAutoplayBlocked(true);
          setAutoplayInfo(pendingPlayOnInfoRef.current);
          autoplayBlockedAudioRef.current = audio;
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

  // Report playback position every 5s while playing, and on pause/track change
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
      const trackTitle = tracks && currentTrackIndex >= 0 && currentTrackIndex < tracks.length
        ? tracks[currentTrackIndex].title
        : undefined;
      sendPositionUpdate({
        showId: activeShow.showId,
        recordingId: selectedRecording,
        trackIndex: currentTrackIndex,
        positionMs,
        durationMs: Math.floor((audio?.duration || 0) * 1000),
        trackTitle,
        status: status === "playing" ? "playing" : "paused",
      });
    }

    if (status === "playing") {
      positionReportRef.current = setInterval(reportPosition, connectConfig.positionUpdateIntervalMs);
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
  }, [status, activeShow, selectedRecording, currentTrackIndex, sendPositionUpdate, connectConfig.positionUpdateIntervalMs]);

  // Announce playback state changes via WebSocket for Connect session
  useEffect(() => {
    if (!activeShow || !selectedRecording || currentTrackIndex < 0) return;
    if (status !== "playing" && status !== "paused") return;

    // Skip announcing if this state change was caused by a server-originated command
    // (prevents feedback loop: server broadcasts → player acts → player announces → server broadcasts)
    if (suppressAnnounceRef.current) {
      suppressAnnounceRef.current = false;
      return;
    }

    const audio = getActiveAudio();
    const positionMs = audio ? Math.floor(audio.currentTime * 1000) : 0;

    const currentTrackTitle = tracks && currentTrackIndex >= 0 && currentTrackIndex < tracks.length
      ? tracks[currentTrackIndex].title
      : undefined;

    announcePlayback({
      showId: activeShow.showId,
      recordingId: selectedRecording,
      trackIndex: currentTrackIndex,
      positionMs,
      durationMs: Math.floor((audio?.duration || 0) * 1000),
      trackTitle: currentTrackTitle,
      status: status === "playing" ? "playing" : "paused",
      date: activeShow.date,
      venue: activeShow.venue,
      location: activeShow.location,
      // Include track list so server can resolve next/prev
      tracks: tracks?.map(t => ({ title: t.title, duration: t.duration })),
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, activeShow, selectedRecording, currentTrackIndex, tracks, announcePlayback]);

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

  // Hydration: when userState arrives from server and no local activeShow, hydrate show info
  useEffect(() => {
    if (hydratedRef.current) return;
    if (!userState || activeShow) return;

    hydratedRef.current = true;
    setActiveShow({
      showId: userState.showId,
      recordings: [],
      bestRecordingId: userState.recordingId,
      date: userState.date ?? "",
      venue: userState.venue ?? "",
      location: userState.location ?? "",
    });
    setSelectedRecording(userState.recordingId);
  }, [userState, activeShow]);

  // Mutual exclusion: stop local playback when another device claims the session
  useEffect(() => {
    if (isActiveDevice) {
      wasActiveRef.current = true;
      ignoringAudioEventsRef.current = false;
    } else if (wasActiveRef.current) {
      // We were active but no longer — another device claimed the session
      wasActiveRef.current = false;
      // Suppress async audio events (pause/error) from src="" so they
      // don't clobber status back to "paused" after we set "idle".
      ignoringAudioEventsRef.current = true;
      const audio = getActiveAudio();
      if (audio && !audio.paused) {
        audio.pause();
        audio.src = "";
      }
      // Mark as idle but keep show/track/position state so the DevicePicker
      // can still send an accurate positionMs if the user transfers back.
      setStatus("idle");
      if ("mediaSession" in navigator) {
        navigator.mediaSession.metadata = null;
      }
    }
  }, [isActiveDevice]);

  // Server-directed stop: the server tells this device to stop playing
  useEffect(() => {
    const handleSessionStop = () => {
      ignoringAudioEventsRef.current = true;
      const audio = getActiveAudio();
      if (audio && !audio.paused) {
        audio.pause();
        audio.src = "";
      }
      // Mark as idle but keep show/track/position state so the DevicePicker
      // can still send an accurate positionMs if the user transfers back.
      setStatus("idle");
      if ("mediaSession" in navigator) {
        navigator.mediaSession.metadata = null;
      }
    };
    window.addEventListener("connect:session_stop", handleSessionStop);
    return () => window.removeEventListener("connect:session_stop", handleSessionStop);
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
    hydratedRef.current = true; // Mark as hydrated since we're actively playing
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

  // Listen for incoming play_on from Connect (another device sent playback to us)
  const pendingPlayOnRef = useRef<{ trackIndex: number; positionMs: number; status: string } | null>(null);

  // When tracks load after a play_on, seek to the correct track + position
  useEffect(() => {
    if (!pendingPlayOnRef.current || !tracks || tracks.length === 0) return;

    const { trackIndex, positionMs, status } = pendingPlayOnRef.current;
    pendingPlayOnRef.current = null;

    // play_on always means "start playing" — never suppress autoplay for transfers

    if (trackIndex > 0 && trackIndex < tracks.length) {
      playTrack(trackIndex);
    }

    if (positionMs > 0) {
      const timer = setTimeout(() => {
        window.dispatchEvent(
          new CustomEvent("connect:seek", { detail: { seconds: positionMs / 1000 } })
        );
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [tracks, playTrack]);

  useEffect(() => {
    function handlePlayOn(e: Event) {
      const detail = (e as CustomEvent).detail as PlaybackState & { fromDeviceName?: string };
      if (!detail) return;
      // Re-enable audio event handlers now that we're resuming local playback
      ignoringAudioEventsRef.current = false;

      // Stash play_on context for autoplay prompt
      pendingPlayOnInfoRef.current = {
        showDate: detail.date ?? "",
        venue: detail.venue ?? "",
        fromDevice: detail.fromDeviceName ?? "another device",
      };

      // Claim the session with the new show state
      claimSession({
        showId: detail.showId,
        recordingId: detail.recordingId,
        trackIndex: detail.trackIndex,
        positionMs: detail.positionMs,
        status: detail.status ?? "playing",
      });

      // If the same recording is already loaded, skip the full playShow flow
      // and directly jump to the correct track + position.  This avoids the
      // race where playRecording resets to track 0 and the useEffect/setTimeout
      // chain fails to seek in time.
      const currentTracks = tracksRef.current;
      if (
        selectedRecordingRef.current === detail.recordingId &&
        currentTracks &&
        currentTracks.length > 0
      ) {
        if (detail.trackIndex >= 0 && detail.trackIndex < currentTracks.length) {
          setCurrentTrackIndex(detail.trackIndex);
        }
        setTimeout(() => {
          const audio = getActiveAudio();
          if (audio) {
            if (detail.positionMs > 0) {
              audio.currentTime = detail.positionMs / 1000;
            }
            // Always play on transfer — receiving a play_on means "start playing here"
            audio.play().catch((err) => {
              if (err instanceof DOMException && err.name === "NotAllowedError") {
                setAutoplayBlocked(true);
                setAutoplayInfo(pendingPlayOnInfoRef.current);
                autoplayBlockedAudioRef.current = audio;
              }
              setStatus("paused");
            });
          }
        }, 300);
        return;
      }

      // Different recording — store seek info for the tracks-loaded useEffect
      pendingPlayOnRef.current = {
        trackIndex: detail.trackIndex,
        positionMs: detail.positionMs,
        status: detail.status,
      };

      // Load and play the show from the provided state
      playShow({
        showId: detail.showId,
        recordings: [],
        bestRecordingId: detail.recordingId,
        date: detail.date ?? "",
        venue: detail.venue ?? "",
        location: detail.location ?? "",
      });
    }
    window.addEventListener("connect:play_on", handlePlayOn);
    return () => window.removeEventListener("connect:play_on", handlePlayOn);
  }, [claimSession, playShow]);

  // React to server state changes when this device IS the active player.
  // Commands (play/pause/seek/next/prev) now go through the server's UserPlaybackState,
  // so we react to state diffs rather than receiving direct command_received messages.
  useEffect(() => {
    if (!isActiveDevice || !userState) {
      prevUserStateRef.current = userState
        ? { isPlaying: userState.isPlaying, trackIndex: userState.trackIndex, positionMs: userState.positionMs }
        : null;
      return;
    }

    const prev = prevUserStateRef.current;
    prevUserStateRef.current = {
      isPlaying: userState.isPlaying,
      trackIndex: userState.trackIndex,
      positionMs: userState.positionMs,
    };

    if (!prev) return;

    const audio = getActiveAudio();
    if (!audio) return;

    // Play/pause changed by server
    if (userState.isPlaying !== prev.isPlaying) {
      suppressAnnounceRef.current = true;
      if (userState.isPlaying) {
        audio.play().catch((err) => {
          if (err instanceof DOMException && err.name === "NotAllowedError") {
            setAutoplayBlocked(true);
            setAutoplayInfo(pendingPlayOnInfoRef.current);
            autoplayBlockedAudioRef.current = audio;
          }
        });
      } else {
        audio.pause();
      }
    }

    // Track changed by server (next/prev)
    if (userState.trackIndex !== prev.trackIndex) {
      suppressAnnounceRef.current = true;
      setCurrentTrackIndex(userState.trackIndex);
    }

    // Seek: only react if position diverged significantly (>2s) from current
    if (userState.trackIndex === prev.trackIndex &&
        userState.isPlaying === prev.isPlaying &&
        Math.abs(userState.positionMs - prev.positionMs) > 2000) {
      const currentMs = audio.currentTime * 1000;
      if (Math.abs(userState.positionMs - currentMs) > 2000) {
        suppressAnnounceRef.current = true;
        audio.currentTime = userState.positionMs / 1000;
      }
    }
  }, [isActiveDevice, userState]);

  const seek = useCallback((fraction: number) => {
    const audio = getActiveAudio();
    if (!audio || !isFinite(audio.duration)) return;
    audio.currentTime = fraction * audio.duration;
  }, []);

  const close = useCallback(() => {
    // Clear error first to prevent stale error flash
    setErrorMessage(null);

    // Announce stop before clearing local audio so the server parks the state
    if (activeShow && selectedRecording) {
      const audio = getActiveAudio();
      const positionMs = audio ? Math.floor(audio.currentTime * 1000) : 0;
      announcePlayback({
        showId: activeShow.showId,
        recordingId: selectedRecording,
        trackIndex: currentTrackIndex,
        positionMs,
        durationMs: Math.floor((audio?.duration || 0) * 1000),
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

    // Stop playback but keep show info loaded (parked state)
    setStatus("idle");
    setCurrentTrackIndex(-1);
    setTracks(null);
    setElapsed(0);
    setDuration(0);
    setIsLoadingTracks(false);
    if ("mediaSession" in navigator) {
      navigator.mediaSession.metadata = null;
    }
    // Note: activeShow and selectedRecording are NOT cleared — this is the parked state
  }, [activeShow, selectedRecording, currentTrackIndex, announcePlayback]);

  const dismiss = useCallback(() => {
    close();
    // Clear all local state
    setActiveShow(null);
    setSelectedRecording(null);
    hydratedRef.current = false;
    // Clear server state
    clearState();
  }, [close, clearState]);

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

  const retryAutoplay = useCallback(() => {
    const audio = autoplayBlockedAudioRef.current ?? getActiveAudio();
    if (!audio) return;
    audio.play().catch(() => { setStatus("paused"); });
    setAutoplayBlocked(false);
    setAutoplayInfo(null);
    autoplayBlockedAudioRef.current = null;
    pendingPlayOnInfoRef.current = null;
  }, []);

  const dismissAutoplay = useCallback(() => {
    setAutoplayBlocked(false);
    setAutoplayInfo(null);
    autoplayBlockedAudioRef.current = null;
    pendingPlayOnInfoRef.current = null;
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
      autoplayInfo,
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
      autoplayInfo,
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
