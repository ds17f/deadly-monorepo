"use client";

import { usePlayer } from "@/contexts/PlayerContext";
import type { ViewedShow } from "@/contexts/PlayerContext";

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

export default function HeaderPlayer() {
  const {
    activeShow,
    viewedShow,
    tracks,
    currentTrackIndex,
    status,
    elapsed,
    duration,
    togglePlayPause,
    nextTrack,
    prevTrack,
    seek,
    close,
    playShow,
  } = usePlayer();

  const currentTrack =
    tracks && currentTrackIndex >= 0 ? tracks[currentTrackIndex] : null;
  const hasNext = tracks ? currentTrackIndex < tracks.length - 1 : false;
  const hasPrevious = currentTrackIndex > 0;
  const isActive = status !== "idle" && currentTrack;

  // Nothing playing and no show viewed — hide completely
  if (!isActive && !viewedShow) return null;

  // Idle state: show play button with show info
  if (!isActive && viewedShow) {
    const info = showLabel(viewedShow);
    return (
      <div className="flex flex-1 items-center justify-end gap-3 overflow-hidden pl-4">
        <div className="min-w-0 flex-1 text-right">
          <p className="truncate text-sm font-medium text-white/70">
            {info.date}
          </p>
          <p className="truncate text-xs text-white/40">{info.venue}</p>
        </div>
        <button
          onClick={() => playShow(viewedShow)}
          className="flex flex-shrink-0 items-center gap-1.5 rounded-full bg-deadly-highlight px-3.5 py-1.5 text-sm font-semibold text-white transition-opacity hover:opacity-90"
        >
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="currentColor"
          >
            <path d="M8 5v14l11-7z" />
          </svg>
          Play
        </button>
      </div>
    );
  }

  // Active state: full-width transport with show + track info
  const isPlaying = status === "playing" || status === "buffering";
  const progress = duration > 0 ? (elapsed / duration) * 100 : 0;
  const info = activeShow ? showLabel(activeShow) : null;
  const trackCount = tracks?.length ?? 0;

  function handleSeek(e: React.MouseEvent<HTMLDivElement>) {
    const rect = e.currentTarget.getBoundingClientRect();
    const fraction = Math.max(
      0,
      Math.min(1, (e.clientX - rect.left) / rect.width)
    );
    seek(fraction);
  }

  return (
    <div className="flex flex-1 items-center gap-3 overflow-hidden pl-4 sm:gap-4">
      {/* Show + track info */}
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline gap-2">
          <p className="truncate text-sm font-medium text-white">
            {currentTrack!.title}
          </p>
          {trackCount > 1 && (
            <span className="flex-shrink-0 text-xs tabular-nums text-white/30">
              {currentTrackIndex + 1}/{trackCount}
            </span>
          )}
        </div>
        {info && (
          <p className="truncate text-xs text-white/40">
            {info.date} — {info.venue}
          </p>
        )}
      </div>

      {/* Seek bar */}
      <div className="flex flex-shrink-0 items-center gap-2">
        <span className="hidden text-[10px] tabular-nums text-white/30 sm:inline">
          {formatTime(elapsed)}
        </span>
        <div
          className="h-1.5 w-20 cursor-pointer rounded-full bg-white/10 sm:w-28 md:w-36"
          onClick={handleSeek}
        >
          <div
            className="h-full rounded-full bg-deadly-highlight transition-[width] duration-150"
            style={{ width: `${progress}%` }}
          />
        </div>
        <span className="hidden text-[10px] tabular-nums text-white/30 sm:inline">
          {formatTime(duration)}
        </span>
      </div>

      {/* Transport controls */}
      <div className="flex flex-shrink-0 items-center gap-0.5">
        <button
          onClick={prevTrack}
          disabled={!hasPrevious && elapsed < 3}
          className="rounded-full p-1.5 text-white/60 transition-colors hover:text-white disabled:text-white/20"
          aria-label="Previous track"
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="currentColor"
          >
            <path d="M6 6h2v12H6zm3.5 6 8.5 6V6z" />
          </svg>
        </button>

        <button
          onClick={togglePlayPause}
          disabled={status === "loading"}
          className="rounded-full bg-white p-1.5 text-deadly-bg transition-opacity hover:opacity-90 disabled:opacity-50"
          aria-label={isPlaying ? "Pause" : "Play"}
        >
          {status === "loading" || status === "buffering" ? (
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="currentColor"
              className="animate-spin"
            >
              <path d="M12 2a10 10 0 0 1 10 10h-2a8 8 0 0 0-8-8V2z" />
            </svg>
          ) : isPlaying ? (
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="currentColor"
            >
              <path d="M6 19h4V5H6zm8-14v14h4V5z" />
            </svg>
          ) : (
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="currentColor"
            >
              <path d="M8 5v14l11-7z" />
            </svg>
          )}
        </button>

        <button
          onClick={nextTrack}
          disabled={!hasNext}
          className="rounded-full p-1.5 text-white/60 transition-colors hover:text-white disabled:text-white/20"
          aria-label="Next track"
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="currentColor"
          >
            <path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z" />
          </svg>
        </button>
      </div>

      {/* Close button */}
      <button
        onClick={close}
        className="flex-shrink-0 rounded-full p-1.5 text-white/25 transition-colors hover:text-white/50"
        aria-label="Close player"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
          <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
        </svg>
      </button>
    </div>
  );
}
