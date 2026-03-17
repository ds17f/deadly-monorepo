import type { ArchiveTrack, PlaybackStatus } from "@/types/player";

function formatTime(seconds: number): string {
  if (!isFinite(seconds) || seconds < 0) return "0:00";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

interface PlayerBarProps {
  track: ArchiveTrack | null;
  status: PlaybackStatus;
  elapsed: number;
  duration: number;
  hasNext: boolean;
  hasPrevious: boolean;
  onTogglePlay: () => void;
  onNext: () => void;
  onPrevious: () => void;
  onSeek: (fraction: number) => void;
  onClose: () => void;
}

export default function PlayerBar({
  track,
  status,
  elapsed,
  duration,
  hasNext,
  hasPrevious,
  onTogglePlay,
  onNext,
  onPrevious,
  onSeek,
  onClose,
}: PlayerBarProps) {
  if (!track || status === "idle") return null;

  const isPlaying = status === "playing" || status === "buffering";
  const progress = duration > 0 ? (elapsed / duration) * 100 : 0;

  function handleSeek(e: React.MouseEvent<HTMLDivElement>) {
    const rect = e.currentTarget.getBoundingClientRect();
    const fraction = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    onSeek(fraction);
  }

  return (
    <div className="fixed bottom-0 left-0 right-0 z-50 border-t border-white/10 bg-deadly-bg/95 backdrop-blur-sm">
      {/* Progress bar — full width, clickable */}
      <div
        className="group h-1 w-full cursor-pointer bg-white/10 transition-all hover:h-2"
        onClick={handleSeek}
      >
        <div
          className="h-full bg-deadly-highlight transition-[width] duration-150"
          style={{ width: `${progress}%` }}
        />
      </div>

      <div className="mx-auto flex max-w-5xl items-center gap-3 px-4 py-2 sm:gap-4 sm:py-3">
        {/* Track info */}
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium text-white">
            {track.title}
          </p>
          <p className="text-xs text-white/40">
            {formatTime(elapsed)} / {formatTime(duration)}
          </p>
        </div>

        {/* Transport controls */}
        <div className="flex items-center gap-1 sm:gap-2">
          {/* Previous */}
          <button
            onClick={onPrevious}
            disabled={!hasPrevious && elapsed < 3}
            className="rounded-full p-2 text-white/70 transition-colors hover:text-white disabled:text-white/20"
            aria-label="Previous track"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M6 6h2v12H6zm3.5 6 8.5 6V6z" />
            </svg>
          </button>

          {/* Play/Pause */}
          <button
            onClick={onTogglePlay}
            disabled={status === "loading"}
            className="rounded-full bg-white p-2 text-deadly-bg transition-opacity hover:opacity-90 disabled:opacity-50 sm:p-2.5"
            aria-label={isPlaying ? "Pause" : "Play"}
          >
            {status === "loading" || status === "buffering" ? (
              <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" className="animate-spin">
                <path d="M12 2a10 10 0 0 1 10 10h-2a8 8 0 0 0-8-8V2z" />
              </svg>
            ) : isPlaying ? (
              <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
                <path d="M6 19h4V5H6zm8-14v14h4V5z" />
              </svg>
            ) : (
              <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
                <path d="M8 5v14l11-7z" />
              </svg>
            )}
          </button>

          {/* Next */}
          <button
            onClick={onNext}
            disabled={!hasNext}
            className="rounded-full p-2 text-white/70 transition-colors hover:text-white disabled:text-white/20"
            aria-label="Next track"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z" />
            </svg>
          </button>
        </div>

        {/* Close button */}
        <button
          onClick={onClose}
          className="rounded-full p-2 text-white/30 transition-colors hover:text-white/60"
          aria-label="Close player"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
          </svg>
        </button>
      </div>
    </div>
  );
}
