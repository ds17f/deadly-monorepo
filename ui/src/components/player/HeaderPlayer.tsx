"use client";

import { useState, useCallback, useRef, useEffect, useMemo } from "react";
import { usePlayer } from "@/contexts/PlayerContext";
import type { ViewedShow } from "@/contexts/PlayerContext";
import { useConnect } from "@/contexts/ConnectContext";
import { useUserData } from "@/contexts/UserDataContext";
import type { AiShowReview } from "@/types/show";
import type { ShowReview } from "@/types/userdata";

import { useInterpolatedPosition } from "@/hooks/useInterpolatedPosition";
import AutoplayPrompt from "./AutoplayPrompt";
import RecordingSelector from "./RecordingSelector";
import TrackList from "./TrackList";
import PlayerRailPanel from "./PlayerRailPanel";
import DevicePicker from "@/components/connect/DevicePicker";
import { useRightRailOverride } from "@/components/shell/RightRail";

function formatTime(seconds: number): string {
  if (!isFinite(seconds) || seconds < 0) return "0:00";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

function formatShowDate(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function showLabel(show: ViewedShow): { date: string; venue: string } {
  return {
    date: formatShowDate(show.date),
    venue: show.venue + (show.location ? `, ${show.location}` : ""),
  };
}

// Shared seek bar + transport, used by both the mobile sheet and the desktop
// docked panel so the controls stay identical.
function SeekBar({
  progress,
  elapsed,
  duration,
  onSeek,
}: {
  progress: number;
  elapsed: number;
  duration: number;
  onSeek: (e: React.MouseEvent<HTMLDivElement>) => void;
}) {
  return (
    <div className="w-full">
      <div
        className="h-1.5 w-full cursor-pointer rounded-full bg-white/10"
        onClick={onSeek}
      >
        <div
          className="h-full rounded-full bg-deadly-highlight"
          style={{ width: `${Math.min(progress, 100)}%` }}
        />
      </div>
      <div className="mt-1 flex justify-between text-[11px] tabular-nums text-white/40">
        <span>{formatTime(elapsed)}</span>
        <span>{formatTime(duration)}</span>
      </div>
    </div>
  );
}

function Transport({
  isPlaying,
  isLoading,
  onPrev,
  onNext,
  onToggle,
  prevDisabled,
  nextDisabled,
  toggleDisabled,
  size = "md",
}: {
  isPlaying: boolean;
  isLoading: boolean;
  onPrev?: () => void;
  onNext?: () => void;
  onToggle: () => void;
  prevDisabled: boolean;
  nextDisabled: boolean;
  toggleDisabled: boolean;
  size?: "sm" | "md" | "lg";
}) {
  const play = size === "lg" ? "h-16 w-16" : size === "sm" ? "h-9 w-9" : "h-12 w-12";
  const playIcon = size === "lg" ? 28 : size === "sm" ? 18 : 24;
  const pauseIcon = size === "lg" ? 26 : size === "sm" ? 16 : 22;
  const sideIcon = size === "sm" ? 20 : 26;
  const gap = size === "sm" ? "gap-4" : "gap-6";
  return (
    <div className={`flex flex-shrink-0 items-center justify-center ${gap}`}>
      <button
        onClick={onPrev}
        disabled={prevDisabled}
        className="text-white/70 transition-colors hover:text-white disabled:text-white/20"
        aria-label="Previous track"
      >
        <svg width={sideIcon} height={sideIcon} viewBox="0 0 24 24" fill="currentColor">
          <path d="M6 6h2v12H6zm3.5 6 8.5 6V6z" />
        </svg>
      </button>
      <button
        onClick={onToggle}
        disabled={toggleDisabled}
        className={`flex items-center justify-center rounded-full bg-white text-deadly-bg transition hover:scale-105 disabled:opacity-50 ${play}`}
        aria-label={isPlaying ? "Pause" : "Play"}
      >
        {isLoading ? (
          <svg width={pauseIcon} height={pauseIcon} viewBox="0 0 24 24" fill="currentColor" className="animate-spin">
            <path d="M12 2a10 10 0 0 1 10 10h-2a8 8 0 0 0-8-8V2z" />
          </svg>
        ) : isPlaying ? (
          <svg width={pauseIcon} height={pauseIcon} viewBox="0 0 24 24" fill="currentColor">
            <path d="M6 19h4V5H6zm8-14v14h4V5z" />
          </svg>
        ) : (
          <svg width={playIcon} height={playIcon} viewBox="0 0 24 24" fill="currentColor">
            <path d="M8 5v14l11-7z" />
          </svg>
        )}
      </button>
      <button
        onClick={onNext}
        disabled={nextDisabled}
        className="text-white/70 transition-colors hover:text-white disabled:text-white/20"
        aria-label="Next track"
      >
        <svg width={sideIcon} height={sideIcon} viewBox="0 0 24 24" fill="currentColor">
          <path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z" />
        </svg>
      </button>
    </div>
  );
}

// Speaker icon + horizontal slider for output volume (desktop docked bar).
// Clicking the speaker toggles mute, remembering the prior level.
function VolumeControl({
  volume,
  setVolume,
}: {
  volume: number;
  setVolume: (v: number) => void;
}) {
  const prevRef = useRef(volume || 1);
  const muted = volume === 0;

  function toggleMute() {
    if (muted) {
      setVolume(prevRef.current || 1);
    } else {
      prevRef.current = volume;
      setVolume(0);
    }
  }

  return (
    <div className="flex items-center gap-1.5">
      <button
        onClick={toggleMute}
        aria-label={muted ? "Unmute" : "Mute"}
        className="text-white/50 transition-colors hover:text-white/80"
      >
        {muted ? (
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
            <path d="M3.63 3.63a.996.996 0 0 0 0 1.41L7.29 8.7 7 9H4a1 1 0 0 0-1 1v4a1 1 0 0 0 1 1h3l3.29 3.29c.63.63 1.71.18 1.71-.71v-4.17l4.18 4.18c-.49.37-1.02.68-1.6.91v2.06a8.99 8.99 0 0 0 3.02-1.4l1.34 1.34a.996.996 0 1 0 1.41-1.41L5.05 3.63a.996.996 0 0 0-1.42 0zM19 12c0 .82-.15 1.61-.41 2.34l1.53 1.53A8.95 8.95 0 0 0 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zm-7-8-1.88 1.88L12 7.76zm4.5 8A4.5 4.5 0 0 0 14 7.97v1.79l2.48 2.48c.01-.08.02-.16.02-.24z" />
          </svg>
        ) : volume < 0.5 ? (
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
            <path d="M7 9v6h3l4 4V5l-4 4H7zm9.5 3a4.5 4.5 0 0 0-2.5-4.03v8.05A4.5 4.5 0 0 0 16.5 12z" />
          </svg>
        ) : (
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
            <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05A4.5 4.5 0 0 0 16.5 12zM14 3.23v2.06A8.99 8.99 0 0 1 14 18.7v2.06A11 11 0 0 0 14 3.23z" />
          </svg>
        )}
      </button>
      <input
        type="range"
        min={0}
        max={1}
        step={0.01}
        value={volume}
        onChange={(e) => setVolume(Number(e.target.value))}
        aria-label="Volume"
        className="h-1 w-24 cursor-pointer accent-deadly-highlight"
      />
    </div>
  );
}

// A single rotating "factoid" drawn from the AI + user reviews, shown in the
// fullscreen view — readable info for when you tab back or it's on a TV.
interface Factoid {
  label: string;
  body: string;
  meta?: string;
}

// Split long prose into readable paragraph-sized chunks for individual cards.
function splitProse(text: string): string[] {
  return text
    .split(/\n\s*\n/)
    .map((p) => p.trim())
    .filter((p) => p.length > 0);
}

// Build the rotation: about → the show (prose) → highlights → band → best
// recording → your review. Each becomes its own readable card.
function buildFactoids(
  review: AiShowReview | null | undefined,
  userReview: ShowReview | null | undefined,
): Factoid[] {
  const cards: Factoid[] = [];
  if (review?.summary) cards.push({ label: "About this show", body: review.summary });
  if (review?.review) {
    splitProse(review.review).forEach((p) =>
      cards.push({ label: "The show", body: p }),
    );
  }
  (review?.key_highlights ?? []).forEach((h) =>
    cards.push({ label: "Highlight", body: h }),
  );
  Object.entries(review?.band_performance ?? {}).forEach(([member, text]) => {
    if (text) cards.push({ label: member, body: text });
  });
  if (review?.best_recording?.reason) {
    cards.push({ label: "Best recording", body: review.best_recording.reason });
  }
  if (userReview && (userReview.notes || userReview.overallRating)) {
    const stars = userReview.overallRating
      ? "★".repeat(userReview.overallRating)
      : undefined;
    cards.push({
      label: "Your review",
      meta: stars,
      body: userReview.notes || "You rated this show.",
    });
  }
  return cards;
}

// Dwell time scaled to length so there's enough time to read (≈200 wpm).
function dwellFor(body: string): number {
  const words = body.trim().split(/\s+/).length;
  return Math.min(26000, Math.max(10000, (words / 3.2) * 1000 + 4500));
}

// Rotates through the factoid cards with a slow crossfade, each lingering long
// enough to read. Holds on a single card; stops when there are none.
function AmbientFactoids({ cards }: { cards: Factoid[] }) {
  const [idx, setIdx] = useState(0);
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    setIdx(0);
    setVisible(true);
  }, [cards]);

  useEffect(() => {
    if (cards.length <= 1) return;
    const dwell = dwellFor(cards[idx]?.body ?? "");
    const tOut = setTimeout(() => setVisible(false), dwell);
    const tNext = setTimeout(() => {
      setIdx((p) => (p + 1) % cards.length);
      setVisible(true);
    }, dwell + 700);
    return () => {
      clearTimeout(tOut);
      clearTimeout(tNext);
    };
  }, [idx, cards]);

  if (cards.length === 0) return null;
  const card = cards[Math.min(idx, cards.length - 1)];

  return (
    <div className="w-full max-w-md">
      <div
        className={`rounded-2xl border border-white/10 bg-white/[0.04] p-6 shadow-xl shadow-black/20 transition-opacity duration-700 ${
          visible ? "opacity-100" : "opacity-0"
        }`}
      >
        <p className="mb-3 flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-deadly-title/80">
          {card.label}
          {card.meta && <span className="text-deadly-star">{card.meta}</span>}
        </p>
        <p className="text-[15px] leading-relaxed text-white/80">{card.body}</p>
      </div>
      {cards.length > 1 && (
        <div className="mt-3 flex flex-wrap justify-center gap-1.5">
          {cards.map((_, i) => (
            <span
              key={i}
              className={`h-1.5 w-1.5 rounded-full transition-colors ${
                i === idx ? "bg-deadly-highlight" : "bg-white/20"
              }`}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default function HeaderPlayer() {
  const {
    activeShow,
    tracks,
    currentTrackIndex,
    status,
    elapsed,
    duration,
    selectedRecording,
    selectRecording,
    isLoadingTracks,
    togglePlayPause,
    nextTrack,
    prevTrack,
    playTrack,
    seek,
    playShow,
    dismiss,
    volume,
    setVolume,
  } = usePlayer();

  const { isConnected, userState, isActiveDevice, claimSession, sendCommand } = useConnect();
  const { getReview } = useUserData();

  // Factoid cards for the fullscreen ambient view: the AI review + the user's
  // own review. Memoized on content so the rotation doesn't reset each render.
  const userReview = activeShow ? getReview(activeShow.showId) : null;
  const factoids = useMemo(
    () => buildFactoids(activeShow?.review, userReview),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [activeShow?.showId, activeShow?.review, userReview?.notes, userReview?.overallRating],
  );

  const [devicePickerOpen, setDevicePickerOpen] = useState(false);
  // Desktop: the queue / device list render into the shell's right column
  // (Spotify-style) rather than as popovers. null = show the page's content.
  const [railMode, setRailMode] = useState<null | "queue" | "devices">(null);
  // The bar expands into a full-screen "now playing" view that slides up;
  // slide it back down to collapse.
  const [expanded, setExpanded] = useState(false);
  // While expanded, chrome (controls + side panel) auto-hides after a few
  // seconds of inactivity into an ambient art-only view; input reveals it.
  const [chromeVisible, setChromeVisible] = useState(true);
  const sheetRef = useRef<HTMLDivElement | null>(null);
  const closeDevicePicker = useCallback(() => setDevicePickerOpen(false), []);
  const setRailOverride = useRightRailOverride();

  // Open the immersive view and ask the browser to go truly full-screen.
  const openFullscreen = useCallback(() => {
    setExpanded(true);
    const el = sheetRef.current;
    if (el?.requestFullscreen) {
      el.requestFullscreen().catch(() => {});
    }
  }, []);

  const collapsePlayer = useCallback(() => {
    setExpanded(false);
    if (typeof document !== "undefined" && document.fullscreenElement) {
      document.exitFullscreen().catch(() => {});
    }
  }, []);

  // Sync our state when the user leaves browser full-screen (e.g. via Esc).
  useEffect(() => {
    function onFsChange() {
      if (!document.fullscreenElement) setExpanded(false);
    }
    document.addEventListener("fullscreenchange", onFsChange);
    return () => document.removeEventListener("fullscreenchange", onFsChange);
  }, []);

  // Idle detection while expanded: reveal chrome on input, hide after a pause.
  useEffect(() => {
    if (!expanded) {
      setChromeVisible(true);
      return;
    }
    let timer: ReturnType<typeof setTimeout>;
    const reveal = () => {
      setChromeVisible(true);
      clearTimeout(timer);
      timer = setTimeout(() => setChromeVisible(false), 3500);
    };
    reveal();
    window.addEventListener("mousemove", reveal);
    window.addEventListener("keydown", reveal);
    window.addEventListener("touchstart", reveal);
    return () => {
      clearTimeout(timer);
      window.removeEventListener("mousemove", reveal);
      window.removeEventListener("keydown", reveal);
      window.removeEventListener("touchstart", reveal);
    };
  }, [expanded]);

  const pendingSeekRef = useRef<{ trackIndex: number; positionMs: number } | null>(null);

  const isLocalPlayback = status === "playing" || status === "paused" || status === "buffering" || status === "loading";
  const interpolatedMs = useInterpolatedPosition(userState, isLocalPlayback, elapsed);

  // Once tracks load after claiming a session, jump to correct track + seek
  useEffect(() => {
    if (!pendingSeekRef.current || !tracks || tracks.length === 0) return;

    const { trackIndex, positionMs } = pendingSeekRef.current;
    pendingSeekRef.current = null;

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

  const handleClaimSession = useCallback(() => {
    if (!userState) return;

    // Store seek info for after tracks load
    pendingSeekRef.current = {
      trackIndex: userState.trackIndex,
      positionMs: userState.positionMs,
    };

    // Claim session on server (will broadcast back with our deviceId)
    claimSession();

    // Load the show from user state
    playShow({
      showId: userState.showId,
      recordings: [],
      bestRecordingId: userState.recordingId,
      date: userState.date ?? "",
      venue: userState.venue ?? "",
      location: userState.location ?? "",
    });
  }, [userState, playShow, claimSession]);

  const handleClaimAndNext = useCallback(() => {
    if (!userState) return;
    pendingSeekRef.current = { trackIndex: userState.trackIndex + 1, positionMs: 0 };
    claimSession();
    playShow({
      showId: userState.showId,
      recordings: [],
      bestRecordingId: userState.recordingId,
      date: userState.date ?? "",
      venue: userState.venue ?? "",
      location: userState.location ?? "",
    });
  }, [userState, playShow, claimSession]);

  const handleClaimAndPrev = useCallback(() => {
    if (!userState) return;
    pendingSeekRef.current = { trackIndex: Math.max(0, userState.trackIndex - 1), positionMs: 0 };
    claimSession();
    playShow({
      showId: userState.showId,
      recordings: [],
      bestRecordingId: userState.recordingId,
      date: userState.date ?? "",
      venue: userState.venue ?? "",
      location: userState.location ?? "",
    });
  }, [userState, playShow, claimSession]);

  const currentTrack =
    tracks && currentTrackIndex >= 0 ? tracks[currentTrackIndex] : null;
  const hasNext = tracks ? currentTrackIndex < tracks.length - 1 : false;
  const hasPrevious = currentTrackIndex > 0;
  const isActive = status !== "idle" && currentTrack;

  // Determine if another device is the active player
  const isRemoteActive = !!(userState && userState.activeDeviceId && !isActiveDevice);
  // Parked: userState exists, no active device, not locally playing
  const isParked = !!(userState && !userState.activeDeviceId && !isActive);

  // Remote track boundary checks (from server-managed track list)
  const remoteTrackCount = userState?.tracks?.length ?? 0;
  const remoteHasNext = remoteTrackCount > 0 && (userState?.trackIndex ?? 0) < remoteTrackCount - 1;
  const remoteHasPrevious = (userState?.trackIndex ?? 0) > 0;

  // Remote control helpers — send commands through the server (state-mediated)
  const remoteTogglePlayPause = useCallback(() => {
    sendCommand(userState?.isPlaying ? "pause" : "play");
  }, [userState, sendCommand]);

  const remoteNext = useCallback(() => {
    sendCommand("next");
  }, [sendCommand]);

  const remotePrev = useCallback(() => {
    if (interpolatedMs > 3000 || !remoteHasPrevious) {
      // Restart current track (matches local player behavior)
      sendCommand("seek", 0);
    } else {
      sendCommand("prev");
    }
  }, [sendCommand, interpolatedMs, remoteHasPrevious]);

  const remoteSeek = useCallback((fraction: number) => {
    if (!userState?.durationMs) return;
    const seekMs = Math.floor(fraction * userState.durationMs);
    sendCommand("seek", seekMs);
  }, [userState, sendCommand]);

  // Unified transport actions — local vs remote vs parked. Local playback wins
  // whenever this device has audio engaged (isActive); the Connect-session
  // ownership (isActiveDevice) must NOT gate local pause/seek, or the controls
  // misroute (e.g. pause → claimSession) whenever we're not in a session yet.
  const handleTogglePlayPause = isActive
    ? togglePlayPause
    : isRemoteActive
      ? remoteTogglePlayPause
      : handleClaimSession; // parked — claim and play

  const handleNext = isActive ? nextTrack : isRemoteActive ? remoteNext : isParked ? handleClaimAndNext : undefined;
  const handlePrev = isActive ? prevTrack : isRemoteActive ? remotePrev : isParked ? handleClaimAndPrev : undefined;

  function handleSeek(e: React.MouseEvent<HTMLDivElement>) {
    const rect = e.currentTarget.getBoundingClientRect();
    const fraction = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    if (isActive) {
      seek(fraction);
    } else if (isRemoteActive || isParked) {
      remoteSeek(fraction);
    }
  }

  // Show info for display
  const displayElapsed = isActive ? elapsed : interpolatedMs / 1000;
  const displayDuration = isActive
    ? duration
    : (userState?.durationMs ?? 0) / 1000;
  const progress = displayDuration > 0 ? (displayElapsed / displayDuration) * 100 : 0;

  // Determine what show info to display
  const showInfo = isActive && activeShow
    ? showLabel(activeShow)
    : userState?.date
      ? { date: formatShowDate(userState.date), venue: (userState.venue ?? "") + (userState.location ? `, ${userState.location}` : "") }
      : null;

  // Track title for display
  const displayTrackTitle = isActive && currentTrack
    ? currentTrack.title
    : userState?.trackTitle ?? null;

  const displayTrackCount = isActive ? (tracks?.length ?? 0) : 0;
  const displayTrackIndex = isActive ? currentTrackIndex : (userState?.trackIndex ?? 0);

  // Determine playing state for button icon
  const displayIsPlaying = isActive
    ? (status === "playing" || status === "buffering")
    : (userState?.isPlaying ?? false);

  const isLoading = status === "loading" || status === "buffering";

  // ── Active / Remote active / Parked / Idle: unified full transport UI ──
  // Subtitle line: show device info when remote, show info when local
  const subtitleLine = isRemoteActive
    ? `${userState?.isPlaying ? "Playing" : "Paused"} on ${userState?.activeDeviceName}`
    : showInfo
      ? `${showInfo.date} — ${showInfo.venue}`
      : null;

  // Cover art (from the viewed/active show); logo when absent (e.g. remote).
  const art = activeShow?.image ?? null;
  const artIsLogo = !art || art.endsWith("/logo.png");
  const artSrc = art ?? "/logo.png";
  const showLoaded = isActive || isParked || isRemoteActive;

  // Shared transport flags for the now-playing sheet.
  const sheetToggleDisabled = isLoadingTracks || !!(isActive && isLoading);
  const sheetIsLoadingIcon = isLoadingTracks || !!(isActive && isLoading);

  // Push the queue / device panel into the shell's right column when toggled
  // (desktop). Releasing it (null) restores the page's right-pane content.
  useEffect(() => {
    if (railMode) {
      setRailOverride(
        <PlayerRailPanel mode={railMode} onClose={() => setRailMode(null)} />,
      );
    } else {
      setRailOverride(null);
    }
    return () => setRailOverride(null);
  }, [railMode, setRailOverride]);

  // Don't strand an open rail panel once the player is cleared.
  useEffect(() => {
    if (!showLoaded && railMode) setRailMode(null);
  }, [showLoaded, railMode]);

  // Fade the immersive chrome (desktop only) when idle → ambient art view.
  const chromeCls = chromeVisible
    ? "lg:opacity-100"
    : "lg:pointer-events-none lg:opacity-0";

  return (
    <div className="relative flex flex-1 items-center pl-4">
    {/* ── Mobile bar: art + info (tap → full-screen sheet) + quick play ── */}
    <div className="flex flex-1 items-center gap-2 overflow-hidden lg:hidden">
      <button
        onClick={() => {
          if (showLoaded) setExpanded(true);
        }}
        disabled={!showLoaded}
        className="flex min-w-0 flex-1 items-center gap-3 text-left disabled:cursor-default"
      >
        {showLoaded && (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={artSrc}
            alt=""
            className={`h-10 w-10 flex-shrink-0 rounded bg-white/5 ${
              artIsLogo ? "object-contain p-1" : "object-cover"
            }`}
            referrerPolicy="no-referrer"
          />
        )}
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium text-white">
            {displayTrackTitle ?? showInfo?.date ?? "--"}
          </p>
          {isRemoteActive ? (
            <p className="truncate text-xs text-deadly-highlight">{subtitleLine}</p>
          ) : showInfo ? (
            <p className="truncate text-xs text-white/40">
              {showInfo.date} — {showInfo.venue}
            </p>
          ) : null}
        </div>
      </button>
      {showLoaded && (
        <button
          onClick={handleTogglePlayPause}
          disabled={isLoadingTracks || !!(isActive && isLoading)}
          className="flex-shrink-0 rounded-full bg-white p-2 text-deadly-bg transition-opacity hover:opacity-90 disabled:opacity-50"
          aria-label={displayIsPlaying ? "Pause" : "Play"}
        >
          {isLoadingTracks || (isActive && isLoading) ? (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" className="animate-spin">
              <path d="M12 2a10 10 0 0 1 10 10h-2a8 8 0 0 0-8-8V2z" />
            </svg>
          ) : displayIsPlaying ? (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M6 19h4V5H6zm8-14v14h4V5z" />
            </svg>
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M8 5v14l11-7z" />
            </svg>
          )}
        </button>
      )}
    </div>

    {/* ── Desktop bar: 3 zones — info · transport over jog bar · actions ── */}
    <div className="hidden flex-1 items-center gap-4 lg:flex">
      {/* LEFT: art + info (click → full now-playing view) */}
      <div className="flex w-1/4 min-w-0 items-center gap-3">
        {showLoaded ? (
          <button
            onClick={() => setExpanded(true)}
            className="flex min-w-0 items-center gap-3 text-left"
            title="Open full player"
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={artSrc}
              alt=""
              className={`h-14 w-14 flex-shrink-0 rounded bg-white/5 ${
                artIsLogo ? "object-contain p-1" : "object-cover"
              }`}
              referrerPolicy="no-referrer"
            />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-white">
                {displayTrackTitle ?? showInfo?.date ?? "--"}
              </p>
              {isRemoteActive ? (
                <p className="truncate text-xs text-deadly-highlight">{subtitleLine}</p>
              ) : showInfo ? (
                <p className="truncate text-xs text-white/40">
                  {showInfo.date} — {showInfo.venue}
                </p>
              ) : null}
            </div>
          </button>
        ) : (
          <span className="truncate text-sm text-white/30">Nothing playing</span>
        )}
      </div>

      {/* CENTER: transport stacked over the jog/seek bar */}
      <div className="flex flex-1 flex-col items-center gap-1.5">
        <Transport
          isPlaying={displayIsPlaying}
          isLoading={isLoadingTracks || !!(isActive && isLoading)}
          onPrev={handlePrev}
          onNext={handleNext}
          onToggle={handleTogglePlayPause}
          prevDisabled={!handlePrev || !!(isActive && !hasPrevious && elapsed < 3) || !!(isRemoteActive && !remoteHasPrevious && interpolatedMs < 3000)}
          nextDisabled={!handleNext || !!(isActive && !hasNext) || (isRemoteActive && !remoteHasNext)}
          toggleDisabled={!showLoaded || isLoadingTracks || !!(isActive && isLoading)}
          size="sm"
        />
        <div className="flex w-full max-w-[520px] items-center gap-2">
          <span className="w-10 text-right text-[11px] tabular-nums text-white/40">
            {formatTime(displayElapsed)}
          </span>
          <div
            className="group h-1.5 flex-1 cursor-pointer rounded-full bg-white/10"
            onClick={handleSeek}
          >
            <div
              className="h-full rounded-full bg-white/60 transition-colors group-hover:bg-deadly-highlight"
              style={{ width: `${Math.min(progress, 100)}%` }}
            />
          </div>
          <span className="w-10 text-[11px] tabular-nums text-white/40">
            {formatTime(displayDuration)}
          </span>
        </div>
      </div>

      {/* RIGHT: queue · devices · volume · fullscreen · clear */}
      <div className="flex w-1/4 items-center justify-end gap-1">
        {isActive && (
          <button
            onClick={() => setRailMode((m) => (m === "queue" ? null : "queue"))}
            className={`rounded-full p-2 transition-colors ${
              railMode === "queue" ? "text-deadly-highlight" : "text-white/50 hover:text-white/80"
            }`}
            aria-label="Queue"
            title="Queue"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z" />
            </svg>
          </button>
        )}
        {isConnected && (
          <button
            onClick={() => setRailMode((m) => (m === "devices" ? null : "devices"))}
            className={`rounded-full p-2 transition-colors ${
              railMode === "devices"
                ? "text-deadly-highlight"
                : isRemoteActive
                  ? "text-deadly-highlight/70 hover:text-deadly-highlight"
                  : "text-white/50 hover:text-white/80"
            }`}
            aria-label="Devices"
            title="Connect to a device"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M1 18v3h4v-3H1zm0-6v3h8v-3H1zm0-6v3h12V6H1zm20 12.59L17.42 15 16 16.41 21 21.41l5-5L24.59 15 21 18.59z" />
            </svg>
          </button>
        )}
        {isActive && <VolumeControl volume={volume} setVolume={setVolume} />}
        {showLoaded && (
          <button
            onClick={openFullscreen}
            className="rounded-full p-2 text-white/50 transition-colors hover:text-white/80"
            aria-label="Full screen"
            title="Full screen"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z" />
            </svg>
          </button>
        )}
        {(isActive || isParked || isRemoteActive) && (
          <button
            onClick={dismiss}
            className="rounded-full p-2 text-white/40 transition-colors hover:text-white/70"
            aria-label="Clear player"
            title="Clear player"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
            </svg>
          </button>
        )}
      </div>
    </div>

      <AutoplayPrompt />

      {/* ── "Now playing" — full-screen on mobile, an immersive full-screen
          view on desktop (real browser fullscreen + idle ambient mode). ── */}
      <div
        ref={sheetRef}
        className={`fixed inset-0 z-[60] flex flex-col bg-gradient-to-b from-deadly-surface to-deadly-bg text-white transition-transform duration-300 ease-out ${
          expanded ? "translate-y-0" : "pointer-events-none translate-y-full"
        }`}
        aria-hidden={!expanded}
      >
        {/* top bar: collapse handle + devices (chrome — fades when idle) */}
        <div className={`flex items-center justify-between px-4 py-3 transition-opacity duration-500 ${chromeCls}`}>
          <button
            onClick={collapsePlayer}
            aria-label="Collapse player"
            className="rounded-full p-2 text-white/60 transition-colors hover:text-white"
          >
            <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
              <path d="M7 10l5 5 5-5z" />
            </svg>
          </button>
          <span className="text-xs font-semibold uppercase tracking-wider text-white/50">
            Now Playing
          </span>
          <div className="relative">
            {isConnected ? (
              <button
                onClick={() => setDevicePickerOpen((o) => !o)}
                aria-label="Connect to device"
                className={`rounded-full p-2 transition-colors ${
                  isRemoteActive ? "text-deadly-highlight" : "text-white/60 hover:text-white"
                }`}
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M1 18v3h4v-3H1zm0-6v3h8v-3H1zm0-6v3h12V6H1zm20 12.59L17.42 15 16 16.41 21 21.41l5-5L24.59 15 21 18.59z" />
                </svg>
              </button>
            ) : (
              <span className="block w-9" />
            )}
            {devicePickerOpen && expanded && (
              <DevicePicker
                currentState={
                  isActive && isActiveDevice && activeShow && selectedRecording
                    ? {
                        showId: activeShow.showId,
                        recordingId: selectedRecording,
                        trackIndex: currentTrackIndex,
                        positionMs: Math.floor(elapsed * 1000),
                        status: status === "playing" ? "playing" : "paused",
                        date: activeShow.date,
                        venue: activeShow.venue,
                        location: activeShow.location,
                      }
                    : userState
                      ? {
                          showId: userState.showId,
                          recordingId: userState.recordingId,
                          trackIndex: userState.trackIndex,
                          positionMs: Math.floor(interpolatedMs),
                          durationMs: userState.durationMs,
                          trackTitle: userState.trackTitle,
                          status: userState.isPlaying ? "playing" : "paused",
                          date: userState.date,
                          venue: userState.venue,
                          location: userState.location,
                        }
                      : null
                }
                onClose={closeDevicePicker}
              />
            )}
          </div>
        </div>

        {/* ── Mobile layout: vertical, full screen ── */}
        <div className="flex flex-1 flex-col overflow-y-auto px-5 pb-8 lg:hidden">
          {/* Full ticket in fullscreen — whole stub (contain), not cropped. */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={artSrc}
            alt=""
            referrerPolicy="no-referrer"
            className={`mx-auto mt-2 max-h-[42vh] w-full max-w-[20rem] rounded-lg object-contain shadow-2xl ${
              artIsLogo ? "aspect-square max-w-[18rem] bg-white/5 p-6" : ""
            }`}
          />
          <div className="mt-5 w-full text-center">
            <p className="text-xl font-bold text-white">
              {displayTrackTitle ?? showInfo?.date ?? "--"}
            </p>
            {subtitleLine && (
              <p className={`mt-1 text-sm ${isRemoteActive ? "text-deadly-highlight" : "text-white/50"}`}>
                {subtitleLine}
              </p>
            )}
            {displayTrackCount > 1 && (
              <p className="mt-1 text-xs tabular-nums text-white/30">
                Track {displayTrackIndex + 1} of {displayTrackCount}
              </p>
            )}
          </div>
          {factoids.length > 0 && (
            <div className="mt-5 flex justify-center">
              <AmbientFactoids cards={factoids} />
            </div>
          )}
          <div className="mt-5">
            <SeekBar progress={progress} elapsed={displayElapsed} duration={displayDuration} onSeek={handleSeek} />
          </div>
          <div className="mt-4">
            <Transport
              isPlaying={displayIsPlaying}
              isLoading={sheetIsLoadingIcon}
              onPrev={handlePrev}
              onNext={handleNext}
              onToggle={handleTogglePlayPause}
              prevDisabled={!handlePrev}
              nextDisabled={!handleNext}
              toggleDisabled={sheetToggleDisabled}
              size="lg"
            />
          </div>
          {activeShow && activeShow.recordings.length > 1 && (
            <RecordingSelector
              recordings={activeShow.recordings}
              selectedId={selectedRecording}
              onSelect={selectRecording}
            />
          )}
          {isActive && (
            <div className="mt-4">
              <h4 className="mb-2 text-sm font-bold text-deadly-title">Tracks</h4>
              <TrackList tracks={tracks} isLoading={isLoadingTracks} currentTrackIndex={currentTrackIndex} status={status} onPlayTrack={playTrack} />
            </div>
          )}
        </div>

        {/* ── Desktop layout: immersive — big art + now-playing on the left,
            a rotating factoid card on the right (the ambient party/TV view).
            Only the top bar + docked controls fade when idle. ── */}
        <div className="hidden min-h-0 flex-1 flex-col overflow-hidden lg:flex">
          {/* Centered column: art → now-playing → rotating factoid card. */}
          <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-6 overflow-y-auto px-10 py-6">
            {/* Full ticket in fullscreen — show the whole stub (contain),
                not the square crop the mini bar uses. */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={artSrc}
              alt=""
              referrerPolicy="no-referrer"
              className={`max-h-[48vh] max-w-[min(90vw,40rem)] flex-shrink-0 rounded-xl object-contain shadow-2xl shadow-black/50 ${
                artIsLogo ? "aspect-square w-full max-w-[min(42vh,24rem)] bg-white/5 p-10" : ""
              }`}
            />
            <div className="max-w-2xl flex-shrink-0 text-center">
              <p className="text-2xl font-bold text-white">
                {displayTrackTitle ?? showInfo?.date ?? "--"}
              </p>
              {subtitleLine && (
                <p className={`mt-1.5 text-base ${isRemoteActive ? "text-deadly-highlight" : "text-white/60"}`}>
                  {subtitleLine}
                </p>
              )}
              {displayTrackCount > 1 && (
                <p className="mt-1 text-sm tabular-nums text-white/30">
                  Track {displayTrackIndex + 1} of {displayTrackCount}
                </p>
              )}
            </div>
            {factoids.length > 0 && (
              <div className="flex w-full flex-shrink-0 justify-center">
                <AmbientFactoids cards={factoids} />
              </div>
            )}
          </div>

          {/* docked controls (chrome — fades when idle) */}
          <div
            className={`flex items-center gap-5 border-t border-white/10 px-8 py-4 transition-opacity duration-500 ${
              chromeVisible ? "opacity-100" : "pointer-events-none opacity-0"
            }`}
          >
            <div className="w-56 min-w-0 flex-shrink-0">
              <p className="truncate font-bold text-white">
                {displayTrackTitle ?? showInfo?.date ?? "--"}
              </p>
              {subtitleLine && (
                <p className={`truncate text-sm ${isRemoteActive ? "text-deadly-highlight" : "text-white/50"}`}>
                  {subtitleLine}
                </p>
              )}
            </div>
            <Transport
              isPlaying={displayIsPlaying}
              isLoading={sheetIsLoadingIcon}
              onPrev={handlePrev}
              onNext={handleNext}
              onToggle={handleTogglePlayPause}
              prevDisabled={!handlePrev}
              nextDisabled={!handleNext}
              toggleDisabled={sheetToggleDisabled}
            />
            <div className="min-w-0 flex-1">
              <SeekBar progress={progress} elapsed={displayElapsed} duration={displayDuration} onSeek={handleSeek} />
            </div>
            <VolumeControl volume={volume} setVolume={setVolume} />
            {displayTrackCount > 1 && (
              <span className="flex-shrink-0 text-xs tabular-nums text-white/40">
                {displayTrackIndex + 1} / {displayTrackCount}
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
