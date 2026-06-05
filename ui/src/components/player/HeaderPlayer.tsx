"use client";

import { useState, useCallback, useRef, useEffect, useMemo, type MouseEvent } from "react";
import { useRouter } from "next/navigation";
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
import DeviceList from "@/components/connect/DeviceList";
import { useRightRailOverride } from "@/components/shell/RightRail";
import { lookupArt, lookupReview } from "@/lib/artCache";

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

// A themed "factoid" card drawn from the AI + user reviews, shown in the
// fullscreen view — readable from across the room when you tab back or it's
// up on a TV. One card per theme; content varies by shape.
interface Factoid {
  label: string;
  meta?: string;
  paragraphs?: string[]; // prose, rendered with paragraph breaks
  bullets?: string[]; // a list (e.g. highlights)
  members?: [string, string][]; // name → note (band performance)
}

// Split prose into paragraphs. Honor real breaks when the review has them;
// most AI reviews are a single unbroken block, so synthesize paragraphs by
// grouping sentences rather than render one giant wall of text.
function splitProse(text: string): string[] {
  const t = text.trim();
  if (!t) return [];
  if (/\n\s*\n/.test(t)) {
    return t.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean);
  }
  if (t.includes("\n")) {
    return t.split(/\n+/).map((p) => p.trim()).filter(Boolean);
  }
  // Split into sentences (after .!? — tolerating a closing quote — when the
  // next char looks like a new sentence), then group by length (~300 chars)
  // so paragraphs read evenly whether sentences are short or long.
  const sentences = t
    .split(/(?<=[.!?]["”’']?)\s+(?=[“"'A-Z])/)
    .map((s) => s.trim())
    .filter(Boolean);
  if (sentences.length <= 1) return [t];

  const TARGET = 300;
  const paras: string[] = [];
  let cur = "";
  for (const s of sentences) {
    cur = cur ? `${cur} ${s}` : s;
    if (cur.length >= TARGET) {
      paras.push(cur);
      cur = "";
    }
  }
  if (cur) {
    if (paras.length && cur.length < 140) paras[paras.length - 1] += ` ${cur}`;
    else paras.push(cur);
  }
  return paras.length ? paras : [t];
}

// One card per theme: About the show (the review) → Highlights → About the
// band (all members together) → About the recording → Your review.
function buildFactoids(
  review: AiShowReview | null | undefined,
  userReview: ShowReview | null | undefined,
): Factoid[] {
  const cards: Factoid[] = [];

  const showParas = review?.review
    ? splitProse(review.review)
    : review?.summary
      ? [review.summary]
      : [];
  if (showParas.length) cards.push({ label: "About the show", paragraphs: showParas });

  const highlights = review?.key_highlights ?? [];
  if (highlights.length) cards.push({ label: "Highlights", bullets: highlights });

  const band = Object.entries(review?.band_performance ?? {}).filter(
    ([, text]) => text && text.length > 0,
  ) as [string, string][];
  if (band.length) cards.push({ label: "About the band", members: band });

  if (review?.best_recording?.reason) {
    cards.push({ label: "About the recording", paragraphs: [review.best_recording.reason] });
  }

  if (userReview && (userReview.notes || userReview.overallRating)) {
    cards.push({
      label: "Your review",
      meta: userReview.overallRating ? "★".repeat(userReview.overallRating) : undefined,
      paragraphs: [userReview.notes || "You rated this show."],
    });
  }
  return cards;
}

function cardText(c: Factoid): string {
  return [
    ...(c.paragraphs ?? []),
    ...(c.bullets ?? []),
    ...(c.members ?? []).map(([m, t]) => `${m} ${t}`),
  ].join(" ");
}

// Dwell scaled to length so there's time to actually read it (≈200 wpm).
function dwellFor(c: Factoid): number {
  const words = cardText(c).trim().split(/\s+/).filter(Boolean).length;
  return Math.min(34000, Math.max(11000, (words / 3.4) * 1000 + 5000));
}

// Rotates through the themed cards with a slow crossfade, each lingering long
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
    const dwell = dwellFor(cards[idx] ?? cards[0]);
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
  // Slightly smaller for the long, multi-paragraph review.
  const big = !(card.paragraphs && card.paragraphs.length > 1);
  const bodyCls = big
    ? "text-lg leading-relaxed text-white/85 lg:text-xl"
    : "text-base leading-relaxed text-white/80 lg:text-lg";

  return (
    <div className="w-full">
      <div className={`transition-opacity duration-700 ${visible ? "opacity-100" : "opacity-0"}`}>
        <p className="mb-4 flex items-center gap-3 text-sm font-bold uppercase tracking-[0.2em] text-deadly-title">
          {card.label}
          {card.meta && <span className="text-deadly-star">{card.meta}</span>}
        </p>

        {card.paragraphs && (
          <div className={`space-y-4 ${bodyCls}`}>
            {card.paragraphs.map((p, i) => (
              <p key={i}>{p}</p>
            ))}
          </div>
        )}

        {card.bullets && (
          <ul className={`space-y-3 ${bodyCls}`}>
            {card.bullets.map((b, i) => (
              <li key={i} className="flex gap-3">
                <span className="mt-px text-deadly-highlight">&bull;</span>
                <span>{b}</span>
              </li>
            ))}
          </ul>
        )}

        {card.members && (
          <div className={`space-y-3 ${bodyCls}`}>
            {card.members.map(([member, text]) => (
              <p key={member}>
                <span className="font-semibold text-white">{member}</span>
                {" — "}
                {text}
              </p>
            ))}
          </div>
        )}
      </div>

      {cards.length > 1 && (
        <div className="mt-5 flex flex-wrap gap-2">
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
    isActiveDevice,
    isRemoteControlling,
    togglePlayPause,
    nextTrack,
    prevTrack,
    playTrack,
    seek,
    ensureTracks,
    dismiss,
    volume,
    setVolume,
  } = usePlayer();

  const { connected: isConnected, state: connectState, serverTimeOffsetMs } = useConnect();
  const { getReview } = useUserData();

  // Factoid cards for the fullscreen ambient view: the AI review + the user's
  // own review. Memoized on content so the rotation doesn't reset each render.
  const userReview = activeShow ? getReview(activeShow.showId) : null;
  const factoids = useMemo(
    () => buildFactoids(activeShow?.review ?? lookupReview(activeShow?.showId), userReview),
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
  const setRailOverride = useRightRailOverride();

  // Open the immersive view and ask the browser to go truly full-screen.
  const openFullscreen = useCallback(() => {
    setExpanded(true);
    const el = sheetRef.current;
    if (el?.requestFullscreen) {
      el.requestFullscreen().catch(() => {});
    }
  }, []);

  const router = useRouter();

  const collapsePlayer = useCallback(() => {
    setExpanded(false);
    if (typeof document !== "undefined" && document.fullscreenElement) {
      document.exitFullscreen().catch(() => {});
    }
  }, []);

  // Tapping the "Now Playing" label jumps to the playing show's page (and
  // collapses the sheet) — the expected way back to the show from the player.
  const playingShowId = activeShow?.showId ?? connectState?.showId ?? null;
  const openPlayingShow = useCallback(() => {
    if (!playingShowId) return;
    collapsePlayer();
    router.push(`/shows/${playingShowId}`);
  }, [playingShowId, collapsePlayer, router]);

  // Sync our state when the user leaves browser full-screen (e.g. via Esc).
  useEffect(() => {
    function onFsChange() {
      if (!document.fullscreenElement) setExpanded(false);
    }
    document.addEventListener("fullscreenchange", onFsChange);
    return () => document.removeEventListener("fullscreenchange", onFsChange);
  }, []);

  // Escape collapses the expanded player. (Browser-fullscreen Esc is already
  // handled by the fullscreenchange listener above; this covers the plain
  // slide-up case where we never entered document fullscreen.)
  useEffect(() => {
    if (!expanded) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") collapsePlayer();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [expanded, collapsePlayer]);

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

  // Smooth, ticking position for a remotely-controlled device's progress bar.
  // The active device reads its own audio clock (elapsed); see the hook.
  const interpolatedMs = useInterpolatedPosition(connectState, serverTimeOffsetMs, isActiveDevice, elapsed * 1000);

  const currentTrack =
    tracks && currentTrackIndex >= 0 ? tracks[currentTrackIndex] : null;
  const hasNext = tracks ? currentTrackIndex < tracks.length - 1 : false;
  const hasPrevious = currentTrackIndex > 0;
  // Local audio engaged (we own the Connect session and play here).
  const isActive = isActiveDevice && status !== "idle";

  // Another device is the active player.
  const isRemoteActive = isRemoteControlling && !!connectState?.activeDeviceId;
  // Parked: a session is loaded but no device is actively playing.
  const isParked = !!connectState?.showId && !connectState?.activeDeviceId && !isActiveDevice;

  // Remote track boundary checks (from the server-managed track list).
  const remoteTrackCount = connectState?.tracks?.length ?? 0;
  const remoteHasNext = remoteTrackCount > 0 && (connectState?.trackIndex ?? 0) < remoteTrackCount - 1;
  const remoteHasPrevious = (connectState?.trackIndex ?? 0) > 0;

  // Anything loaded to show (local audio or a shared Connect session).
  const showLoaded = !!activeShow || !!connectState?.showId;

  // Transport routes through the player context, which sends the right Connect
  // command for us — a local action when active, a server command when
  // remote-controlling or parked. Gate on showLoaded so a bare bar is inert.
  const handleTogglePlayPause = togglePlayPause;
  const handleNext = showLoaded ? nextTrack : undefined;
  const handlePrev = showLoaded ? prevTrack : undefined;

  // Space toggles play/pause whenever a show is loaded (main screen or
  // fullscreen) and stops the page from scrolling/paging. Read the handler
  // through a ref so the listener isn't re-bound every render.
  const toggleRef = useRef(handleTogglePlayPause);
  toggleRef.current = handleTogglePlayPause;
  const spaceControlsPlayback = isActive || isParked || isRemoteActive;
  useEffect(() => {
    if (!spaceControlsPlayback) return;
    function onKey(e: KeyboardEvent) {
      if (e.code !== "Space" && e.key !== " ") return;
      const el = e.target as HTMLElement | null;
      const tag = el?.tagName;
      // Don't hijack typing or a focused control (it handles Space itself).
      if (
        tag === "INPUT" ||
        tag === "TEXTAREA" ||
        tag === "SELECT" ||
        tag === "BUTTON" ||
        el?.isContentEditable
      ) {
        return;
      }
      e.preventDefault(); // no page scroll/paging
      toggleRef.current();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [spaceControlsPlayback]);

  function handleSeek(e: React.MouseEvent<HTMLDivElement>) {
    const rect = e.currentTarget.getBoundingClientRect();
    const fraction = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    seek(fraction);
  }

  // Display values: the active device reads its local audio; everyone else
  // reads the authoritative ConnectState (interpolated position, server track).
  const remoteTrack = connectState && connectState.tracks.length > 0
    ? connectState.tracks[connectState.trackIndex] ?? null
    : null;

  const displayElapsed = isActiveDevice ? elapsed : interpolatedMs / 1000;
  const displayDuration = isActiveDevice ? duration : (connectState?.durationMs ?? 0) / 1000;
  const progress = displayDuration > 0 ? (displayElapsed / displayDuration) * 100 : 0;

  const showInfo = isActiveDevice && activeShow
    ? showLabel(activeShow)
    : connectState?.date
      ? { date: formatShowDate(connectState.date), venue: (connectState.venue ?? "") + (connectState.location ? `, ${connectState.location}` : "") }
      : null;

  const displayTrackTitle = isActiveDevice
    ? currentTrack?.title ?? null
    : remoteTrack?.title ?? null;

  const displayTrackCount = isActiveDevice ? (tracks?.length ?? 0) : (connectState?.tracks.length ?? 0);
  const displayTrackIndex = isActiveDevice ? currentTrackIndex : (connectState?.trackIndex ?? 0);

  const displayIsPlaying = isActiveDevice
    ? (status === "playing" || status === "buffering")
    : (connectState?.playing ?? false);

  const isLoading = status === "loading" || status === "buffering";

  // Subtitle: which device is playing when remote, else the show line.
  const subtitleLine = isRemoteActive
    ? `${connectState?.playing ? "Playing" : "Paused"} on ${connectState?.activeDeviceName}`
    : showInfo
      ? `${showInfo.date} — ${showInfo.venue}`
      : null;

  // Cover art: prefer what playback handed us, else recover it by showId from
  // the art cache (so claim/handoff/refresh paths that lack an image still show
  // the cover). Square stealie fallback when we've never seen art for this show.
  const realArt = activeShow?.image ?? lookupArt(activeShow?.showId) ?? null;
  const artIsLogo = !realArt || realArt.endsWith("/logo.png");
  const artSrc = artIsLogo ? "/cover-fallback.png" : realArt;

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

  // Playlist availability: show it whenever a session is loaded — playing
  // locally, parked, OR remote-controlling another device. The queue must
  // always reflect the current recording at the proper track. When we're not
  // the active device we may not have tracks yet (or they were never fetched),
  // so load them without playing so the rail can render.
  const showPlaylist = showLoaded;
  useEffect(() => {
    if (showPlaylist && !tracks && !isLoadingTracks && selectedRecording) {
      ensureTracks();
    }
  }, [showPlaylist, tracks, isLoadingTracks, selectedRecording, ensureTracks]);

  // Track index to highlight in the queue: our local audio index when we're the
  // active player, else the authoritative server index (the local audio index
  // does not follow remote skips — only the active device's does).
  const queueTrackIndex = isActiveDevice ? currentTrackIndex : (connectState?.trackIndex ?? currentTrackIndex);
  // Likewise the playing indicator: server truth when remote.
  const queueStatus = isActiveDevice ? status : (connectState?.playing ? "playing" : "paused");

  // Rail track pick. playTrack routes through Connect: active → jump locally and
  // broadcast; remote/parked → ask the server to move and our audio follows.
  const handleRailPlay = useCallback(
    (index: number) => playTrack(index),
    [playTrack],
  );

  // Fade the immersive chrome (desktop only) when idle → ambient art view.
  const chromeCls = chromeVisible
    ? "lg:opacity-100"
    : "lg:pointer-events-none lg:opacity-0";

  // Clicking the bar opens the full player — but ignore clicks that land on an
  // actual control (buttons, links, the volume range input, or the seek bar)
  // so they still do their own thing. This replaces per-cluster
  // stopPropagation, which was swallowing clicks on the empty space around the
  // controls too — leaving only the bare gap between clusters clickable.
  const handleBarClick = useCallback(
    (e: MouseEvent<HTMLElement>) => {
      if (!showLoaded) return;
      if ((e.target as HTMLElement).closest("button, a, input, [data-no-expand]")) return;
      setExpanded(true);
    },
    [showLoaded],
  );

  return (
    <div className="relative flex flex-1 items-center pl-4">
    {/* ── Mobile bar: art + info (tap → full-screen sheet) + quick play ── */}
    <div
      className={`flex flex-1 items-center gap-2 overflow-hidden lg:hidden ${showLoaded ? "cursor-pointer" : ""}`}
      onClick={handleBarClick}
    >
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
            className="h-10 w-10 flex-shrink-0 rounded bg-white/5 object-cover"
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

    {/* ── Desktop bar: 3 zones — info · transport over jog bar · actions ──
        Clicking anywhere that isn't an interactive control opens the full
        player (see handleBarClick). ── */}
    <div
      className={`hidden flex-1 items-center gap-4 lg:flex ${showLoaded ? "cursor-pointer" : ""}`}
      onClick={handleBarClick}
    >
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
              className="h-14 w-14 flex-shrink-0 rounded bg-white/5 object-cover"
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
            data-no-expand
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
        {showLoaded && (
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
          <button
            onClick={openPlayingShow}
            disabled={!playingShowId}
            aria-label="Go to show"
            className="text-xs font-semibold uppercase tracking-wider text-white/50 transition-colors enabled:hover:text-white disabled:cursor-default"
          >
            Now Playing
          </button>
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
              <div className="absolute right-0 top-12 z-10 w-72 rounded-lg border border-white/10 bg-deadly-surface p-3 shadow-xl">
                <DeviceList />
              </div>
            )}
          </div>
        </div>

        {/* ── Mobile layout: native player parity — a compact ticket + show
            info header, the transport docked beneath it, then the TRACK LIST
            as the scrolling body. No liner notes here (that ambient/review
            content stays a desktop-only "TV mode" affair). The whole point is
            that the tracklist leads, reachable the instant the sheet opens. ── */}
        <div className="flex min-h-0 flex-1 flex-col overflow-hidden px-5 pb-8 lg:hidden">
          {/* Compact header: small ticket beside the show info. */}
          <div className="flex flex-shrink-0 items-center gap-4 pt-1">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={artSrc}
              alt=""
              referrerPolicy="no-referrer"
              className={`h-20 w-20 flex-shrink-0 rounded-lg shadow-lg ${
                artIsLogo ? "object-cover" : "object-contain"
              }`}
            />
            <div className="min-w-0 flex-1">
              <p className="truncate text-lg font-bold text-white">
                {displayTrackTitle ?? showInfo?.date ?? "--"}
              </p>
              {isRemoteActive ? (
                subtitleLine && (
                  <p className="mt-0.5 truncate text-sm text-deadly-highlight">{subtitleLine}</p>
                )
              ) : showInfo ? (
                <>
                  <p className="mt-0.5 truncate text-sm text-white/70">{showInfo.date}</p>
                  {showInfo.venue && (
                    <p className="truncate text-xs text-white/50">{showInfo.venue}</p>
                  )}
                </>
              ) : null}
              {displayTrackCount > 1 && (
                <p className="mt-0.5 text-xs tabular-nums text-white/30">
                  Track {displayTrackIndex + 1} of {displayTrackCount}
                </p>
              )}
            </div>
          </div>

          {/* Transport + seek, docked compactly under the header. */}
          <div className="mt-4 flex-shrink-0">
            <SeekBar progress={progress} elapsed={displayElapsed} duration={displayDuration} onSeek={handleSeek} />
          </div>
          <div className="mt-3 flex-shrink-0">
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
            <div className="flex-shrink-0">
              <RecordingSelector
                recordings={activeShow.recordings}
                selectedId={selectedRecording}
                onSelect={selectRecording}
              />
            </div>
          )}

          {/* Tracks — the main event. `fill` lets TrackList own the remaining
              height and scroll; it already renders its own "Tracks" heading. */}
          {showPlaylist && (
            <div className="mt-4 flex min-h-0 flex-1 flex-col">
              <TrackList fill tracks={tracks} isLoading={isLoadingTracks} currentTrackIndex={queueTrackIndex} status={queueStatus} onPlayTrack={handleRailPlay} showId={activeShow?.showId} recordingId={selectedRecording} />
            </div>
          )}
        </div>

        {/* ── Desktop layout: immersive. The cover art + now-playing stay
            anchored at the top (never scroll away — the ticket is the star);
            the rotating review content flows wide below it and scrolls on its
            own if long. Ambient party/TV view. ── */}
        <div className="hidden min-h-0 flex-1 overflow-hidden lg:flex">
          {/* Main column: ticket + rotating review + docked controls. */}
          <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
          {/* Anchored header: cover art + now-playing. Centers vertically when
              there's no review content to show below. */}
          <div
            className={`flex flex-col items-center gap-4 px-10 ${
              factoids.length > 0 ? "flex-shrink-0 pt-10 pb-4" : "flex-1 justify-center py-8"
            }`}
          >
            {/* Full ticket in fullscreen — show the whole stub (contain),
                not the square crop the mini bar uses. */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={artSrc}
              alt=""
              referrerPolicy="no-referrer"
              className={`flex-shrink-0 rounded-xl ${
                artIsLogo
                  ? "aspect-square w-[min(34vh,18rem)] object-cover"
                  : "max-h-[34vh] max-w-[min(90vw,30rem)] object-contain shadow-2xl shadow-black/50"
              }`}
            />
            <div className="max-w-3xl flex-shrink-0 text-center">
              <p className="text-3xl font-bold text-white">
                {displayTrackTitle ?? showInfo?.date ?? "--"}
              </p>
              {subtitleLine && (
                <p className={`mt-2 text-lg ${isRemoteActive ? "text-deadly-highlight" : "text-white/70"}`}>
                  {subtitleLine}
                </p>
              )}
              {displayTrackCount > 1 && (
                <p className="mt-1 text-sm tabular-nums text-white/30">
                  Track {displayTrackIndex + 1} of {displayTrackCount}
                </p>
              )}
            </div>
          </div>

          {/* Review content — flows wide (~80%), scrolls here if long so the
              ticket above stays put. */}
          {factoids.length > 0 && (
            <div className="min-h-0 flex-1 overflow-y-auto px-[10%] pb-6 pt-2">
              <div className="mx-auto w-full max-w-5xl">
                <AmbientFactoids cards={factoids} />
              </div>
            </div>
          )}

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
          </div>{/* end main column */}

          {/* Playlist rail — shown whenever a local show is loaded (playing or
              parked) so you can pick a track. It's chrome: when idle it both
              fades AND collapses its width to 0, so the ambient art re-centers
              into the freed space (Spotify-style). */}
          {showPlaylist && (
            <aside
              className={`flex flex-shrink-0 flex-col overflow-hidden transition-all duration-500 ${
                chromeVisible
                  ? "w-80 border-l border-white/10 px-4 pb-2 pt-4 opacity-100"
                  : "pointer-events-none w-0 p-0 opacity-0"
              }`}
            >
              <TrackList
                tracks={tracks}
                isLoading={isLoadingTracks}
                currentTrackIndex={queueTrackIndex}
                status={queueStatus}
                onPlayTrack={handleRailPlay}
                showId={activeShow?.showId}
                recordingId={selectedRecording}
                fill
              />
            </aside>
          )}
        </div>
      </div>
    </div>
  );
}
