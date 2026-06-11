"use client";

import { useState, useRef, useCallback, useEffect, useMemo } from "react";
import type { ArchiveTrack, PlaybackStatus } from "@/types/player";
import { fetchArchiveTracks, fetchArchiveShowMeta } from "@/lib/archive";
import * as analytics from "@/lib/analytics";
import { updatePlaybackPosition, addRecentShow } from "@/lib/userDataApi";
import { notifyUserDataChanged } from "@/lib/userDataEvents";
import { useAuth } from "@/contexts/AuthContext";
import { rememberArt, lookupArt, rememberReview, lookupReview } from "@/lib/artCache";
import { getAutoAdvanceEnabled } from "@/lib/playbackPrefs";
import { PlayerContext } from "@/contexts/PlayerContext";
import type { ViewedShow } from "@/contexts/PlayerContext";
import { useConnect } from "@/contexts/ConnectContext";

const AUTO_ADVANCE_DELAY_MS = 15_000; // ADR-0010 chunk 2: end-of-show countdown

// The /api/shows[/:id|/:id/next] catalog shape → a playable ViewedShow.
interface ShowMetaResponse {
  showId: string;
  date: string | null;
  venue: string | null;
  city: string | null;
  state: string | null;
  bestRecordingId: string | null;
  image: string | null;
}
function metaToViewedShow(m: ShowMetaResponse): ViewedShow {
  return {
    showId: m.showId,
    recordings: [],
    bestRecordingId: m.bestRecordingId,
    date: m.date ?? "",
    venue: m.venue ?? "",
    location: [m.city, m.state].filter(Boolean).join(", "),
    image: m.image,
    review: null,
  };
}
const PREV_TRACK_THRESHOLD = 3; // seconds
const AUDIO_RETRY_DELAYS = [0, 1000, 2000];
const GAPLESS_PRELOAD_THRESHOLD = 2; // seconds before end to start preloading
const DEFAULT_VOLUME = 0.5; // mid-range until the user sets it themselves

export default function PlayerProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user } = useAuth();
  const {
    state: connectState,
    myDeviceId,
    connected,
    sendCommand,
    onVolumeMessage,
    reportVolume,
    setLocalPlaybackSource,
    serverTimeOffsetMs,
  } = useConnect();

  // Derived Connect roles. "Active" = this device owns the session and plays
  // audio locally. "Remote-controlling" = a shared show is loaded but another
  // device (or none) is active, so our transport routes through the server.
  const isActiveDevice =
    connectState !== null && myDeviceId !== null && connectState.activeDeviceId === myDeviceId;
  const isRemoteControlling =
    connectState !== null && connectState.showId !== null && !isActiveDevice;

  const [activeShow, setActiveShow] = useState<ViewedShow | null>(null);
  const [viewedShow, setViewedShow] = useState<ViewedShow | null>(null);
  const [tracks, setTracks] = useState<ArchiveTrack[] | null>(null);
  const [currentTrackIndex, setCurrentTrackIndex] = useState(-1);
  const [status, setStatus] = useState<PlaybackStatus>("idle");
  // ADR-0010 chunk 2: pending end-of-show auto-advance, surfaced to the UI so the
  // full player can preview the next show + a "Next up in Ns" banner. nextShow
  // carries the display data; secondsRemaining ticks 15→0.
  const [autoAdvance, setAutoAdvance] = useState<{
    secondsRemaining: number;
    nextShow: ViewedShow;
  } | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const [duration, setDuration] = useState(0);
  const [selectedRecording, setSelectedRecording] = useState<string | null>(
    null
  );
  const [isLoadingTracks, setIsLoadingTracks] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [volume, setVolumeState] = useState(DEFAULT_VOLUME);
  const [autoplayBlocked, setAutoplayBlocked] = useState(false);
  const [autoplayInfo, setAutoplayInfo] = useState<{ showDate: string; venue: string; fromDevice: string } | null>(null);
  const [pendingCommand, setPendingCommand] = useState<string | null>(null);
  const [pendingTransfer, setPendingTransfer] = useState<string | null>(null);
  const autoplayBlockedAudioRef = useRef<HTMLAudioElement | null>(null);

  // Dual audio elements for gapless playback
  const audioARef = useRef<HTMLAudioElement | null>(null);
  const audioBRef = useRef<HTMLAudioElement | null>(null);
  const activeAudioRef = useRef<"A" | "B">("A");
  const retryCountRef = useRef(0);
  const tracksRef = useRef<ArchiveTrack[] | null>(null);
  const currentTrackIndexRef = useRef(-1);
  const preloadedNextRef = useRef(false);
  const activeShowRef = useRef<ViewedShow | null>(null);
  const selectedRecordingRef = useRef<string | null>(null);
  const statusRef = useRef<PlaybackStatus>("idle");
  const volumeRef = useRef(DEFAULT_VOLUME);
  // Hydrating tracks for a device that should NOT auto-play (paused session, or
  // a non-active client preloading so a future transfer is instant).
  const suppressAutoplayRef = useRef(false);
  // Position (ms) to apply once the freshly-loaded track has metadata. Set on
  // Connect hydration so we resume at the server's interpolated position.
  const pendingSeekMsRef = useRef<number | null>(null);
  const sendCommandRef = useRef(sendCommand);
  // ADR-0010 chunk 2: pending end-of-show auto-advance. Held in refs so the
  // (non-React) audio `ended` handler can trigger the latest coordinator and so
  // the countdown can be canceled by any subsequent play.
  const autoAdvanceTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const onShowCompleteRef = useRef<((completedShowId: string) => void) | null>(null);
  // ADR-0010: true once real playback has happened this session. Guards the
  // `ended` end-of-show signal against firing on restore/cold-start, when the
  // player is rehydrated at the last track and the audio element can emit a
  // spurious `ended` (mirrors Android's hasPlayedThisSession).
  const hasPlayedThisSessionRef = useRef(false);
  const reportVolumeRef = useRef(reportVolume);
  const isActiveDeviceRef = useRef(false);
  const isRemoteControllingRef = useRef(false);
  // Guards the one-shot track re-assert (below) so position broadcasts arriving
  // before our load echoes back don't make us re-send it repeatedly.
  const reassertingTracksRef = useRef(false);
  // Last server epoch seen. A change means the server restarted (and rehydrated
  // the session), which is the only situation a still-playing device reclaims.
  const lastEpochRef = useRef<number | null>(null);
  // Name of the most recent OTHER active device, used to attribute the
  // "Play on this device?" autoplay prompt after a transfer to us.
  const prevActiveDeviceNameRef = useRef<string | null>(null);
  const prevIsActiveDeviceRef = useRef(false);

  // ── Playback analytics (playback_start / playback_end) ──────────────
  // Mirrors the mobile client: a 1s dwell gates playback_start so queue-load
  // churn and rapid skips don't fire phantom starts (which would skew
  // trending). playback_end fires for the previously-committed track when the
  // track changes, on natural end, and on close.
  const committedPlaybackRef = useRef<
    { key: string; showId: string; recordingId: string; trackNumber: number; durationMs: number } | null
  >(null);
  const lastElapsedMsRef = useRef(0);
  const dwellTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Record a recent play once per show-listening session (signed-in only).
  // Read auth through a ref so the dwell-commit closure isn't stale.
  const userIdRef = useRef<string | undefined>(undefined);
  const lastRecentShowRef = useRef<string | null>(null);
  // Source attributed to the next playback_start; reset to "auto_advance"
  // after each emit so only an explicit user action overrides it.
  const nextPlaybackSourceRef = useRef<string>("auto_advance");

  useEffect(() => {
    lastElapsedMsRef.current = Math.floor(elapsed * 1000);
  }, [elapsed]);

  useEffect(() => {
    userIdRef.current = user?.id;
  }, [user?.id]);

  const emitPlaybackEnd = useCallback((reason: string) => {
    const c = committedPlaybackRef.current;
    if (!c) return;
    committedPlaybackRef.current = null;
    const listenedMs = Math.min(lastElapsedMsRef.current, c.durationMs || lastElapsedMsRef.current);
    analytics.track("playback_end", {
      show_id: c.showId,
      recording_id: c.recordingId,
      track_index: c.trackNumber,
      listened_ms: listenedMs,
      duration_ms: c.durationMs,
      reason,
    });
  }, []);

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
    statusRef.current = status;
  }, [status]);
  useEffect(() => {
    sendCommandRef.current = sendCommand;
  }, [sendCommand]);
  useEffect(() => {
    reportVolumeRef.current = reportVolume;
  }, [reportVolume]);
  useEffect(() => {
    isActiveDeviceRef.current = isActiveDevice;
  }, [isActiveDevice]);
  useEffect(() => {
    isRemoteControllingRef.current = isRemoteControlling;
  }, [isRemoteControlling]);

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

    // Apply a Connect-hydration seek once the track has enough metadata to
    // accept a currentTime assignment.
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
        hasPlayedThisSessionRef.current = true;
        retryCountRef.current = 0;
        setAutoplayBlocked(false);
        setAutoplayInfo(null);
        autoplayBlockedAudioRef.current = null;
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
          // Tell the server about the auto-advance so other devices follow.
          sendCommandRef.current("next");
        } else {
          setStatus("paused");
          // ADR-0010 Chunk 1: the final track finished on its own — the positive
          // "show completed" signal. `ended` only fires on natural EOF (a user
          // stop/pause doesn't fire it; load errors go to the "error" listener),
          // and this is the last-track branch. The `hasPlayedThisSession` guard
          // rejects the restore/cold-start case (rehydrated at the last track,
          // where the audio element can emit a spurious `ended` with no real
          // playback) — without it, a hard refresh spuriously auto-advances.
          const completedShowId = activeShowRef.current?.showId ?? "";
          if (completedShowId && hasPlayedThisSessionRef.current) {
            hasPlayedThisSessionRef.current = false;
            console.info(`🏁 [SHOW-COMPLETE] onShowCompleted(showId=${completedShowId})`);
            // ADR-0010 chunk 2: drive chronological auto-advance off this signal.
            onShowCompleteRef.current?.(completedShowId);
          }
          // Last track finished on its own — no track change will drive the
          // playback_end, so emit the completed end here.
          const c = committedPlaybackRef.current;
          if (c) {
            committedPlaybackRef.current = null;
            analytics.track("playback_end", {
              show_id: c.showId,
              recording_id: c.recordingId,
              track_index: c.trackNumber,
              listened_ms: c.durationMs,
              duration_ms: c.durationMs,
              reason: "completed",
            });
          }
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

  // Restore persisted volume on mount, before the audio elements are created.
  // Use the stored value only if it's a real, audible setting (> 0). A missing
  // key coerces to 0 (Number(null) === 0), and an earlier bug persisted that 0
  // back — so treat 0/invalid as "unset" and keep the mid-range default.
  useEffect(() => {
    const stored = Number(localStorage.getItem("deadly_volume"));
    if (isFinite(stored) && stored > 0 && stored <= 1) {
      volumeRef.current = stored;
      setVolumeState(stored);
    }
  }, []);

  // Apply volume to both audio elements and persist it. When this device is the
  // active Connect player, report the level so remote controllers' sliders track.
  useEffect(() => {
    volumeRef.current = volume;
    if (audioARef.current) audioARef.current.volume = volume;
    if (audioBRef.current) audioBRef.current.volume = volume;
    localStorage.setItem("deadly_volume", String(volume));
    if (isActiveDeviceRef.current) {
      reportVolumeRef.current(Math.round(volume * 100));
    }
  }, [volume]);

  const setVolume = useCallback((v: number) => {
    setVolumeState(Math.max(0, Math.min(1, v)));
  }, []);

  // Apply incoming volume commands (0..100) from a remote controller — only
  // when we're the active device actually producing audio.
  useEffect(() => {
    return onVolumeMessage((vol: number) => {
      if (!isActiveDeviceRef.current) return;
      setVolume(vol / 100);
    });
  }, [onVolumeMessage, setVolume]);

  // Report current volume when this device becomes the active player.
  useEffect(() => {
    if (isActiveDevice && !prevIsActiveDeviceRef.current) {
      reportVolumeRef.current(Math.round(volumeRef.current * 100));
    }
    prevIsActiveDeviceRef.current = isActiveDevice;
  }, [isActiveDevice]);

  // Create audio elements once
  useEffect(() => {
    const audioA = new Audio();
    audioA.preload = "auto";
    audioA.volume = volumeRef.current;
    audioARef.current = audioA;

    const audioB = new Audio();
    audioB.preload = "none";
    audioB.volume = volumeRef.current;
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

    // Hydrating for a paused/non-active device: load (so a transfer is instant)
    // but don't start playback.
    if (suppressAutoplayRef.current) {
      suppressAutoplayRef.current = false;
      audio.preload = "auto";
      audio.load();
      setStatus("paused");
    } else {
      setStatus("loading");
      audio.play().catch((err) => {
        if (err instanceof DOMException && err.name === "NotAllowedError") {
          setAutoplayBlocked(true);
          setAutoplayInfo(buildAutoplayInfo());
          autoplayBlockedAudioRef.current = audio;
        }
        setStatus("paused");
      });
    }

    updateMediaSession(track);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentTrackIndex, tracks]);

  // Detect the settled current track → emit playback_start (after a 1s dwell)
  // and playback_end for the outgoing track. The dwell is what keeps rapid
  // skips / queue-load churn from firing phantom starts that would skew
  // trending; see the refs near the top.
  useEffect(() => {
    const valid =
      !!activeShow &&
      !!selectedRecording &&
      !!tracks &&
      currentTrackIndex >= 0 &&
      currentTrackIndex < tracks.length;
    const track = valid ? tracks![currentTrackIndex] : null;
    const newKey = valid
      ? `${activeShow!.showId}|${selectedRecording}|${currentTrackIndex}`
      : null;

    const committed = committedPlaybackRef.current;

    // Outgoing committed track ends the instant the tuple changes — read the
    // elapsed now, before the incoming track's first timeupdate resets it.
    if (committed && committed.key !== newKey) {
      if (!newKey) {
        emitPlaybackEnd("stopped");
      } else {
        const listenedMs = lastElapsedMsRef.current;
        const completed = committed.durationMs > 0 && listenedMs >= committed.durationMs - 2000;
        emitPlaybackEnd(completed ? "completed" : "skipped");
      }
    }

    if (dwellTimerRef.current) {
      clearTimeout(dwellTimerRef.current);
      dwellTimerRef.current = null;
    }

    if (valid && track && newKey && (!committed || committed.key !== newKey)) {
      const info = {
        key: newKey,
        showId: activeShow!.showId,
        recordingId: selectedRecording!,
        trackNumber: track.track,
        durationMs: Math.floor((track.duration || 0) * 1000),
      };
      dwellTimerRef.current = setTimeout(() => {
        dwellTimerRef.current = null;
        const source = nextPlaybackSourceRef.current;
        nextPlaybackSourceRef.current = "auto_advance";
        committedPlaybackRef.current = info;
        analytics.track("playback_start", {
          show_id: info.showId,
          recording_id: info.recordingId,
          track_index: info.trackNumber,
          source,
        });
        // Record a recent play once per show session (not per auto-advanced
        // track), signed-in only. This is what populates /me/recent — the web
        // player previously never wrote it.
        if (userIdRef.current && lastRecentShowRef.current !== info.showId) {
          lastRecentShowRef.current = info.showId;
          // Notify the /me recent surfaces so they refresh without a reload.
          addRecentShow(info.showId)
            .then(() => notifyUserDataChanged())
            .catch(() => {});
        }
      }, 1000);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeShow?.showId, selectedRecording, currentTrackIndex, tracks]);

  // Register MediaSession action handlers
  useEffect(() => {
    if (!("mediaSession" in navigator)) return;

    navigator.mediaSession.setActionHandler("play", () => togglePlayPause());
    navigator.mediaSession.setActionHandler("pause", () => togglePlayPause());
    navigator.mediaSession.setActionHandler("previoustrack", () => prevTrack());
    navigator.mediaSession.setActionHandler("nexttrack", () => nextTrack());
    navigator.mediaSession.setActionHandler("seekto", (details) => {
      if (details.seekTime == null) return;
      const audio = getActiveAudio();
      if (audio) audio.currentTime = details.seekTime;
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

  // Persist playback position via REST every 15s while playing, and once on
  // pause. This is the cross-platform position store (mobile hydrates from it);
  // real-time device-to-device sync goes over the Connect WebSocket below.
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
      updatePlaybackPosition({
        showId: activeShow.showId,
        recordingId: selectedRecording,
        trackIndex: currentTrackIndex,
        positionMs,
      }).catch(() => {});
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
  }, [status, activeShow, selectedRecording, currentTrackIndex]);

  // Real-time position reporting over Connect (~5s) while this device is the
  // active player, so remote controllers' progress bars interpolate correctly.
  useEffect(() => {
    if (!isActiveDevice || !connectState?.playing) return;
    const id = setInterval(() => {
      const audio = getActiveAudio();
      if (audio && !audio.paused) {
        sendCommandRef.current("position", { positionMs: Math.round(audio.currentTime * 1000) });
      }
    }, 5000);
    return () => clearInterval(id);
  }, [isActiveDevice, connectState?.playing]);

  // ADR-0011 Chunk B: expose this device's local playback to the Connect
  // heartbeat so it can renew the ownership lease (heals an ownerless session
  // after a server restart / socket blip). The getter reads live refs, so a
  // single registration stays current; null when nothing is loaded.
  useEffect(() => {
    setLocalPlaybackSource(() => {
      const audio = getActiveAudio();
      const recordingId = selectedRecordingRef.current;
      if (!audio || !recordingId) return null;
      return {
        playing: !audio.paused,
        recordingId,
        positionMs: Math.round(audio.currentTime * 1000),
      };
    });
    return () => setLocalPlaybackSource(null);
  }, [setLocalPlaybackSource]);

  // ── React to authoritative ConnectState broadcasts ───────────────────
  // Single reconciliation path keyed on the server's monotonic version: clear
  // resolved pending commands, hydrate audio when the shared recording changes,
  // pause when another device takes over, and sync transport when we're active.
  useEffect(() => {
    if (!connectState) return;

    // Remember the active OTHER device's name for autoplay-prompt attribution.
    if (connectState.activeDeviceId && connectState.activeDeviceId !== myDeviceId) {
      prevActiveDeviceNameRef.current = connectState.activeDeviceName;
    }

    // Clear a pending command once the server confirms the expected state.
    setPendingCommand((prev) => {
      if (prev === "play" && connectState.playing) return null;
      if (prev === "pause" && !connectState.playing) return null;
      if (prev === "next" || prev === "prev" || prev === "seek") return null;
      return prev;
    });

    // Clear a pending transfer once a device becomes active (handoff resolved).
    setPendingTransfer((prev) => {
      if (!prev) return null;
      if (connectState.activeDeviceId) return null;
      return prev;
    });

    // Hydrate local audio when the server's recording changes (or on first
    // state). Every connected client stays loaded so transfers are instant.
    if (connectState.recordingId && connectState.recordingId !== selectedRecordingRef.current) {
      const autoPlay = isActiveDevice && connectState.playing;
      const interpolatedPosMs = connectState.playing
        ? connectState.positionMs + ((Date.now() + serverTimeOffsetMs) - connectState.positionTs)
        : connectState.positionMs;
      pendingSeekMsRef.current = interpolatedPosMs;
      if (!autoPlay) suppressAutoplayRef.current = true;
      const showId = connectState.showId ?? "";
      setActiveShow({
        showId,
        recordings: [],
        bestRecordingId: connectState.recordingId,
        date: connectState.date ?? "",
        venue: connectState.venue ?? "",
        location: connectState.location ?? "",
        // The server state carries no cover art — recover it by showId from the
        // art cache (populated whenever this device viewed/played the show).
        image: lookupArt(showId),
        review: lookupReview(showId),
      });
      setSelectedRecording(connectState.recordingId);
      const targetTrack = connectState.trackIndex;
      playRecording(connectState.recordingId).then((fetchedTracks) => {
        if (fetchedTracks.length > 0) {
          setCurrentTrackIndex(targetTrack < fetchedTracks.length ? targetTrack : 0);
        }
      });
      // Resolve real show metadata (date/venue/location) from Archive.org — the
      // session state may carry none (position-only hydrate). Client-resolve.
      fetchArchiveShowMeta(connectState.recordingId).then((meta) => {
        if (!meta) return;
        setActiveShow((prev) =>
          prev && prev.showId === showId
            ? {
                ...prev,
                date: meta.date || prev.date,
                venue: meta.venue || prev.venue,
                location: meta.location || prev.location,
              }
            : prev
        );
      });
      return; // defer play/pause sync until tracks load
    }

    // Did the server restart? An epoch change is the explicit, authoritative
    // signal (vs. inferring it from null-active + empty-tracks coincidences).
    const prevEpoch = lastEpochRef.current;
    const serverRestarted = prevEpoch !== null && connectState.epoch !== prevEpoch;
    lastEpochRef.current = connectState.epoch;

    // Reclaim only when the server restarted and rehydrated the session (so it
    // has no active device) while we're still playing this recording — take
    // ownership back without a gap. reassertingTracksRef keeps us in this mode
    // while our reclaim load is in flight (epoch is unchanged on later states).
    // A deliberate transition (transfer park, stop) keeps the same epoch, so it
    // is never mistaken for a restart.
    const audio = getActiveAudio();
    const reclaim =
      connectState.activeDeviceId === null &&
      !!audio && !audio.paused &&
      connectState.recordingId === selectedRecordingRef.current &&
      (serverRestarted || reassertingTracksRef.current);

    // Not the active device and not reclaiming → another device owns the session
    // (a real takeover) OR we were parked/stopped during a transfer. Either way,
    // stop local audio and report where we left off so a handoff resumes right.
    if (!isActiveDevice && !reclaim) {
      reassertingTracksRef.current = false;
      if (audio && !audio.paused) {
        const positionMs = Math.round(audio.currentTime * 1000);
        audio.pause();
        sendCommand("position", { positionMs });
      }
      return;
    }

    // We ARE the active device, or we're reclaiming a restarted session.
    if (isActiveDevice || reclaim) {
      if (!audio) return;

      // handleLoad (re)claims us active, honors the index/position we pass, and
      // — because we pass autoplay — restores playing, so this refills tracks
      // (and re-establishes the active device on a reclaim) without a gap.
      const localTracks = tracksRef.current;
      if (connectState.tracks.length > 0 && !reclaim) {
        reassertingTracksRef.current = false;
      }
      if (
        (connectState.tracks.length === 0 || reclaim) &&
        localTracks && localTracks.length > 0 &&
        connectState.recordingId === selectedRecordingRef.current &&
        !reassertingTracksRef.current
      ) {
        reassertingTracksRef.current = true;
        // This `load` only needs to backfill the `tracks` array the ownerless
        // session lacked — it must NOT move the transport. Which cursor is
        // authoritative depends on why we're reasserting:
        //   • reclaim (server restarted while we kept playing locally) → LOCAL
        //     is the truth; the server's restored index/position lag behind.
        //   • fresh claim of an ownerless session (we just seek+play'd to take
        //     it over) → the SERVER cursor is the truth; our local index is
        //     stale because we were a remote and hadn't applied the seek yet.
        //     Using local here clobbered the just-seeked track back to the old
        //     one (tap Franklin's Tower, get the previously-cued track).
        const idx = reclaim ? currentTrackIndexRef.current : connectState.trackIndex;
        const loadIndex = idx >= 0 ? idx : 0;
        const positionMs = reclaim
          ? Math.round(audio.currentTime * 1000)
          : connectState.positionMs;
        sendCommand("load", {
          showId: connectState.showId ?? activeShowRef.current?.showId ?? "",
          recordingId: connectState.recordingId,
          tracks: localTracks.map((t) => ({
            title: t.title,
            durationMs: Math.round((t.duration || 0) * 1000),
          })),
          trackIndex: loadIndex,
          positionMs,
          durationMs: Math.round((localTracks[loadIndex]?.duration ?? 0) * 1000),
          date: connectState.date ?? activeShowRef.current?.date,
          venue: connectState.venue ?? activeShowRef.current?.venue,
          location: connectState.location ?? activeShowRef.current?.location,
          autoplay: reclaim ? !audio.paused : connectState.playing,
        });
      }

      // On a reclaim, don't sync transport DOWN to the stale playing:false /
      // saved position — we're the source of truth until the server echoes our
      // load back (next version, by which point activeDeviceId === us).
      if (reclaim) return;

      // Same rule for an end-of-show park: we "became active" only because we
      // announced our OWN completion and the server claimed us active (ADR-0011).
      // We're parked at the end of the show waiting for the note effect below to
      // advance — our position is authoritative, the server's positionMs is the
      // stale pre-end value. Syncing would seek back and replay the tail, re-firing
      // completion and resetting the countdown. The note effect owns the transition.
      if (connectState.pendingAdvance) return;

      // Track changed by a remote next/prev/seek. The trackEffect (keyed on
      // currentTrackIndex) fully owns loading + starting the NEW track — so we
      // must NOT also touch the outgoing track here. The old code fell through
      // and called audio.play() on the still-loaded previous track, then the
      // trackEffect swapped src and played again — briefly starting the wrong
      // track (an audible blip of the previous one) before the selected track
      // loads. Sync the index and let the trackEffect take it from there.
      const indexChanging = connectState.trackIndex !== currentTrackIndexRef.current;
      if (indexChanging) {
        // If the session is paused, tell the trackEffect to load without playing.
        if (!connectState.playing) suppressAutoplayRef.current = true;
        setCurrentTrackIndex(connectState.trackIndex);
        return;
      }

      // Same track — reconcile position and play/pause on the already-loaded
      // element. Translate Date.now() into server-clock via serverTimeOffsetMs
      // before subtracting positionTs — a skewed client clock otherwise jumps by
      // the skew on every periodic broadcast and trips the guard below, causing
      // audible skipping.
      const interpolatedPosMs = connectState.playing
        ? connectState.positionMs + ((Date.now() + serverTimeOffsetMs) - connectState.positionTs)
        : connectState.positionMs;
      const serverPositionS = interpolatedPosMs / 1000;
      if (isFinite(audio.duration) && Math.abs(audio.currentTime - serverPositionS) > 1) {
        audio.currentTime = serverPositionS;
      }

      if (connectState.playing && audio.paused) {
        audio.play().catch((err) => {
          if (err instanceof DOMException && err.name === "NotAllowedError") {
            setAutoplayBlocked(true);
            setAutoplayInfo(buildAutoplayInfo());
            autoplayBlockedAudioRef.current = audio;
          }
        });
      } else if (!connectState.playing && !audio.paused) {
        audio.pause();
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectState?.version]);

  function buildAutoplayInfo(): { showDate: string; venue: string; fromDevice: string } {
    const show = activeShowRef.current;
    return {
      showDate: show?.date ?? connectState?.date ?? "",
      venue: show?.venue ?? connectState?.venue ?? "",
      fromDevice: prevActiveDeviceNameRef.current ?? "another device",
    };
  }

  function updateMediaSession(track: ArchiveTrack) {
    if (!("mediaSession" in navigator)) return;
    const showId = activeShowRef.current?.showId ?? "";
    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: "Grateful Dead",
      album: showId.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()),
    });
    // Re-assert the track handlers after each metadata swap. iOS can drop the
    // previously-registered action handlers when the now-playing item changes,
    // and once they're gone the lock screen falls back to its default ±skip
    // buttons instead of previous/next track. Setting them here (and NOT
    // setting seekforward/seekbackward) keeps it on real track controls.
    navigator.mediaSession.setActionHandler("previoustrack", () => prevTrack());
    navigator.mediaSession.setActionHandler("nexttrack", () => nextTrack());
    navigator.mediaSession.playbackState = "playing";
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

  // Load the loaded show's track list WITHOUT starting playback, so a parked
  // player can show its playlist and let the user pick a track to begin. Leaves
  // currentTrackIndex at -1, so the play effect (guarded on index >= 0) stays
  // put. No-op if tracks are already loaded or a fetch is in flight.
  const ensureTracks = useCallback(async () => {
    if ((tracksRef.current && tracksRef.current.length > 0) || isLoadingTracks) return;
    const recId = selectedRecordingRef.current ?? activeShowRef.current?.bestRecordingId ?? null;
    if (!recId) return;
    setIsLoadingTracks(true);
    try {
      const fetched = await fetchArchiveTracks(recId);
      if (fetched.length > 0) setTracks(fetched);
    } catch {
      /* leave tracks null; the rail just won't render */
    }
    setIsLoadingTracks(false);
  }, [isLoadingTracks]);

  const playShow = useCallback(
    async (show: ViewedShow) => {
      // A manual play cancels any pending auto-advance countdown. (When the
      // countdown itself fires it clears the ref/state before calling, so this
      // is a no-op in that path.)
      if (autoAdvanceTimerRef.current) {
        clearInterval(autoAdvanceTimerRef.current);
        autoAdvanceTimerRef.current = null;
      }
      setAutoAdvance(null);
      nextPlaybackSourceRef.current = "browse";
      setActiveShow(show);
      // Remember the cover so a page refresh (which rehydrates from the
      // server's art-less ConnectState) can restore it instead of the logo.
      // Only when we actually have art — claim/handoff paths call playShow
      // with no image and must NOT clobber a previously-stored cover.
      rememberArt(show.showId, show.image);
      rememberReview(show.showId, show.review);
      const recId =
        show.bestRecordingId ?? show.recordings[0]?.identifier ?? null;
      setSelectedRecording(recId);
      if (!recId) return;
      const fetchedTracks = await playRecording(recId);
      if (fetchedTracks.length === 0) return;
      // Claim the session on the server and start everyone at track 0.
      sendCommand("load", {
        showId: show.showId,
        recordingId: recId,
        tracks: fetchedTracks.map((t) => ({
          title: t.title,
          durationMs: Math.round((t.duration || 0) * 1000),
        })),
        trackIndex: 0,
        positionMs: 0,
        durationMs: Math.round((fetchedTracks[0]?.duration ?? 0) * 1000),
        date: show.date,
        venue: show.venue,
        location: show.location,
        autoplay: true,
      });
    },
    [playRecording, sendCommand]
  );

  // ADR-0010 chunk 2 / §7: end-of-show advance, driven by the `onShowComplete`
  // signal. In a Connect session it `announce`s the next show + deadline so the
  // shared note drives the countdown + advance on EVERY device (see the effect
  // below). Offline it runs a purely local countdown. Reads no transport state
  // to decide whether to advance.
  const onShowComplete = useCallback(
    async (completedShowId: string) => {
      // ADR-0010 ship gate: per-device opt-out. Gates whether THIS device
      // initiates an advance when it's the one playing (announce/countdown/advance).
      if (!getAutoAdvanceEnabled()) {
        console.info("[auto-advance] disabled by preference — not advancing");
        return;
      }
      // Resolve the next chronological show. The browser has no catalog (shows
      // are static SSG), so ask the API, which holds it in memory.
      let next: ShowMetaResponse;
      try {
        const res = await fetch(`/api/shows/${encodeURIComponent(completedShowId)}/next`);
        if (!res.ok) {
          console.info("[auto-advance] no next show after", completedShowId);
          return;
        }
        next = await res.json();
      } catch {
        return;
      }

      if (connected) {
        // In a session: announce. The server parks playback + sets the shared
        // note; the effect below renders the countdown and (on the active
        // device) advances at the deadline. Deadline is in SERVER time so every
        // device ticks to the same instant.
        const deadline = Date.now() + serverTimeOffsetMs + AUTO_ADVANCE_DELAY_MS;
        console.info(`[auto-advance] announce → ${next.showId} @ ${deadline}`);
        sendCommandRef.current("announce_next", { showId: next.showId, deadline });
        return;
      }

      // Offline: local-only countdown, then advance.
      const viewed = metaToViewedShow(next);
      if (autoAdvanceTimerRef.current) clearInterval(autoAdvanceTimerRef.current);
      let remaining = Math.round(AUTO_ADVANCE_DELAY_MS / 1000);
      setAutoAdvance({ secondsRemaining: remaining, nextShow: viewed });
      autoAdvanceTimerRef.current = setInterval(() => {
        remaining -= 1;
        if (remaining > 0) {
          setAutoAdvance({ secondsRemaining: remaining, nextShow: viewed });
          return;
        }
        if (autoAdvanceTimerRef.current) clearInterval(autoAdvanceTimerRef.current);
        autoAdvanceTimerRef.current = null;
        setAutoAdvance(null);
        playShow(viewed);
      }, 1000);
    },
    [playShow, connected, serverTimeOffsetMs]
  );

  // ADR-0010 §7: in a Connect session, the shared `pendingAdvance` note is the
  // source of the countdown on EVERY device. Resolve the show, tick down to the
  // server deadline locally, and — on the active device — advance when it's
  // present and the deadline passes (uniform rule: cancel clears the note;
  // "play now" moves the deadline to now). The active device advancing sends a
  // load, which clears the note for everyone.
  useEffect(() => {
    if (!connected) return; // offline: the local driver above owns autoAdvance
    const note = connectState?.pendingAdvance;
    if (!note) {
      setAutoAdvance(null);
      return;
    }

    let cancelled = false;
    let interval: ReturnType<typeof setInterval> | null = null;
    (async () => {
      let viewed: ViewedShow;
      try {
        const res = await fetch(`/api/shows/${encodeURIComponent(note.showId)}`);
        if (!res.ok) return;
        viewed = metaToViewedShow(await res.json());
      } catch {
        return;
      }
      if (cancelled) return;

      // Cache the next show's cover now, so when this (remote) device follows the
      // advance its now-playing player can resolve the art (the active device
      // caches it via playShow; remotes otherwise never would). No-op for
      // ticket-less shows — the player's logo fallback handles those.
      if (viewed.image) rememberArt(viewed.showId, viewed.image);

      const tick = () => {
        const serverNow = Date.now() + serverTimeOffsetMs;
        const remaining = Math.ceil((note.deadline - serverNow) / 1000);
        if (remaining <= 0) {
          if (interval) clearInterval(interval);
          setAutoAdvance(null);
          if (isActiveDeviceRef.current) playShow(viewed); // load clears the note
          return;
        }
        setAutoAdvance({ secondsRemaining: remaining, nextShow: viewed });
      };
      tick();
      interval = setInterval(tick, 1000);
    })();

    return () => {
      cancelled = true;
      if (interval) clearInterval(interval);
    };
  }, [
    connected,
    connectState?.pendingAdvance?.showId,
    connectState?.pendingAdvance?.deadline,
    serverTimeOffsetMs,
    playShow,
  ]);

  // Cancel the pending advance. In a session, tell everyone (server clears the
  // note); offline, just stop the local countdown.
  const cancelAutoAdvance = useCallback(() => {
    if (autoAdvanceTimerRef.current) {
      clearInterval(autoAdvanceTimerRef.current);
      autoAdvanceTimerRef.current = null;
    }
    setAutoAdvance(null);
    if (connected) sendCommandRef.current("cancel_advance");
  }, [connected]);

  // "Play now". Active device (or offline) plays immediately — its load clears
  // the note for everyone. A remote asks the active device to advance now.
  const playNextNow = useCallback(() => {
    const next = autoAdvance?.nextShow;
    if (connected && !isActiveDevice) {
      sendCommandRef.current("advance_now");
      return;
    }
    if (!next) return;
    if (autoAdvanceTimerRef.current) {
      clearInterval(autoAdvanceTimerRef.current);
      autoAdvanceTimerRef.current = null;
    }
    setAutoAdvance(null);
    playShow(next);
  }, [autoAdvance, connected, isActiveDevice, playShow]);

  useEffect(() => {
    onShowCompleteRef.current = onShowComplete;
  }, [onShowComplete]);

  // Play a show then jump to a specific track once its tracks load. Matches by
  // title first (the stable identity of a favorite song), then by track number.
  const pendingTrackRef = useRef<{ title: string; number?: number | null } | null>(null);

  const playShowTrack = useCallback(
    (show: ViewedShow, trackTitle: string, trackNumber?: number | null) => {
      pendingTrackRef.current = { title: trackTitle, number: trackNumber };
      playShow(show);
      nextPlaybackSourceRef.current = "favorites";
    },
    [playShow]
  );

  const playTrack = useCallback((index: number) => {
    const durationMs = tracksRef.current?.[index]?.duration
      ? Math.round(tracksRef.current[index].duration * 1000)
      : 0;
    // Manual track selection — attribute the resulting playback_start.
    nextPlaybackSourceRef.current = "track_list";
    if (isRemoteControllingRef.current) {
      // Remote: move the shared cursor, then express the play-intent. A tap on a
      // queue item means "play this track" — same as the Play button — but a bare
      // `seek` only repositions: it never claims ownership or sets playing. On an
      // ownerless session (activeDeviceId=null, e.g. all devices idle/ghosted)
      // nobody acts on the seek, so it silently does nothing; on a paused active
      // device the seek alone wouldn't resume. Following with `play` fixes both —
      // handlePlay claims THIS device when the session is ownerless (and is a
      // safe no-op when another device is already playing, so it never hijacks).
      setPendingCommand("play");
      sendCommandRef.current("seek", { trackIndex: index, positionMs: 0, durationMs });
      sendCommandRef.current("play");
      return;
    }
    // Reset gapless state since user is manually selecting
    preloadedNextRef.current = false;
    const inactive = getInactiveAudio();
    if (inactive) {
      inactive.src = "";
      inactive.removeAttribute("src");
    }
    setCurrentTrackIndex(index);
    sendCommandRef.current("seek", { trackIndex: index, positionMs: 0, durationMs });
  }, []);

  // Resolve a pending favorite-song jump after the recording's tracks arrive.
  useEffect(() => {
    if (!pendingTrackRef.current || !tracks || tracks.length === 0) return;
    const { title, number } = pendingTrackRef.current;
    pendingTrackRef.current = null;
    let idx = tracks.findIndex((t) => t.title === title);
    if (idx < 0 && number != null) idx = tracks.findIndex((t) => t.track === number);
    // idx 0 already auto-plays via playRecording; only re-point when it differs.
    if (idx > 0) playTrack(idx);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tracks, playTrack]);

  const togglePlayPause = useCallback(() => {
    if (isRemoteControllingRef.current && connectState) {
      // Remote control: send the command and spin until the server confirms.
      const action = connectState.playing ? "pause" : "play";
      setPendingCommand(action);
      sendCommandRef.current(action);
      return;
    }
    const audio = getActiveAudio();
    if (!audio) return;
    if (audio.paused) {
      audio.play().catch(() => {});
      sendCommandRef.current("play");
    } else {
      audio.pause();
      sendCommandRef.current("pause");
    }
  }, [connectState]);

  const nextTrack = useCallback(() => {
    if (isRemoteControllingRef.current) {
      setPendingCommand("next");
      sendCommandRef.current("next");
      return;
    }
    if (!tracksRef.current) return;
    setCurrentTrackIndex((prev) => {
      if (prev < tracksRef.current!.length - 1) return prev + 1;
      return prev;
    });
    sendCommandRef.current("next");
  }, []);

  const prevTrack = useCallback(() => {
    if (isRemoteControllingRef.current) {
      setPendingCommand("prev");
      sendCommandRef.current("prev");
      return;
    }
    const audio = getActiveAudio();
    if (!audio || !tracksRef.current) return;
    if (audio.currentTime > PREV_TRACK_THRESHOLD || currentTrackIndexRef.current === 0) {
      audio.currentTime = 0;
      // Propagate the restart as a same-index seek so remote viewers' scrubbers
      // snap back immediately instead of waiting for the next position report —
      // matches iOS/Android, where restart-current sends seek(index, 0).
      sendCommandRef.current("seek", {
        trackIndex: currentTrackIndexRef.current,
        positionMs: 0,
        durationMs: isFinite(audio.duration) ? Math.round(audio.duration * 1000) : 0,
      });
    } else {
      setCurrentTrackIndex((prev) => Math.max(0, prev - 1));
      sendCommandRef.current("prev");
    }
  }, []);

  const seek = useCallback((fraction: number) => {
    if (isRemoteControllingRef.current && connectState) {
      const positionMs = Math.round(fraction * connectState.durationMs);
      setPendingCommand("seek");
      sendCommandRef.current("seek", {
        trackIndex: connectState.trackIndex,
        positionMs,
        durationMs: connectState.durationMs,
      });
      return;
    }
    const audio = getActiveAudio();
    if (!audio || !isFinite(audio.duration)) return;
    audio.currentTime = fraction * audio.duration;
    sendCommandRef.current("seek", {
      trackIndex: currentTrackIndexRef.current,
      positionMs: Math.round(fraction * audio.duration * 1000),
      durationMs: Math.round(audio.duration * 1000),
    });
  }, [connectState]);

  const transferTo = useCallback((targetDeviceId: string) => {
    if (!connectState?.showId) return;
    setPendingTransfer(targetDeviceId);
    sendCommandRef.current("transfer", { targetDeviceId });
  }, [connectState?.showId]);

  const close = useCallback(() => {
    // Clear error first to prevent stale error flash
    setErrorMessage(null);

    // End any tracked playback before we tear down local audio.
    if (dwellTimerRef.current) {
      clearTimeout(dwellTimerRef.current);
      dwellTimerRef.current = null;
    }
    emitPlaybackEnd("stopped");
    // Allow re-recording a recent if the same show is played again later.
    lastRecentShowRef.current = null;

    // Park the shared session on the server: persists position, drops the
    // active device, keeps the show loaded so any device can resume. No-op
    // server-side when nothing is loaded.
    sendCommandRef.current("stop");

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
  }, [emitPlaybackEnd]);

  const dismiss = useCallback(() => {
    close(); // parks the shared session (sends "stop")
    // Clear all local state too — the docked player goes away entirely.
    setActiveShow(null);
    setSelectedRecording(null);
  }, [close]);

  const selectRecording = useCallback(
    (identifier: string) => {
      setSelectedRecording(identifier);
      // If already playing, switch to the new recording
      if (statusRef.current !== "idle") {
        playRecording(identifier);
      }
    },
    [playRecording]
  );

  const retryAutoplay = useCallback(() => {
    const audio = autoplayBlockedAudioRef.current ?? getActiveAudio();
    if (!audio) return;
    audio.play().catch(() => { setStatus("paused"); });
    setAutoplayBlocked(false);
    setAutoplayInfo(null);
    autoplayBlockedAudioRef.current = null;
  }, []);

  const dismissAutoplay = useCallback(() => {
    setAutoplayBlocked(false);
    setAutoplayInfo(null);
    autoplayBlockedAudioRef.current = null;
  }, []);

  // Wrap setViewedShow so simply viewing a show page also remembers its art,
  // giving hydration a cover to restore after a refresh even if the last
  // playback came through an art-less Connect path.
  const updateViewedShow = useCallback((show: ViewedShow | null) => {
    setViewedShow(show);
    if (show?.image) rememberArt(show.showId, show.image);
    if (show?.review) rememberReview(show.showId, show.review);
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
      volume,
      isActiveDevice,
      isRemoteControlling,
      pendingCommand,
      pendingTransfer,
      transferTo,
      setViewedShow: updateViewedShow,
      setVolume,
      selectRecording,
      playShow,
      playShowTrack,
      playRecording,
      playTrack,
      ensureTracks,
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
      autoAdvance,
      cancelAutoAdvance,
      playNextNow,
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
      volume,
      isActiveDevice,
      isRemoteControlling,
      pendingCommand,
      pendingTransfer,
      transferTo,
      setVolume,
      selectRecording,
      playShow,
      playShowTrack,
      playRecording,
      playTrack,
      ensureTracks,
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
      updateViewedShow,
      autoAdvance,
      cancelAutoAdvance,
      playNextNow,
    ]
  );

  return (
    <PlayerContext.Provider value={value}>
      {children}
    </PlayerContext.Provider>
  );
}
