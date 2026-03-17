import type { ArchiveTrack, PlaybackStatus } from "@/types/player";

function formatDuration(seconds: number): string {
  if (!seconds || !isFinite(seconds)) return "";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

interface TrackListProps {
  tracks: ArchiveTrack[] | null;
  isLoading: boolean;
  currentTrackIndex: number;
  status: PlaybackStatus;
  onPlayTrack: (index: number) => void;
}

export default function TrackList({
  tracks,
  isLoading,
  currentTrackIndex,
  status,
  onPlayTrack,
}: TrackListProps) {
  if (isLoading) {
    return (
      <div className="mt-4 space-y-2">
        <h4 className="text-sm font-bold text-deadly-title">Tracks</h4>
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="flex items-center gap-3 rounded px-2 py-1.5">
            <div className="h-4 w-4 animate-pulse rounded bg-white/10" />
            <div className="h-4 flex-1 animate-pulse rounded bg-white/10" />
            <div className="h-4 w-8 animate-pulse rounded bg-white/10" />
          </div>
        ))}
      </div>
    );
  }

  if (!tracks || tracks.length === 0) return null;

  return (
    <div className="mt-4">
      <h4 className="mb-2 text-sm font-bold text-deadly-title">Tracks</h4>
      <div className="max-h-80 overflow-y-auto">
        {tracks.map((track, index) => {
          const isCurrent = index === currentTrackIndex;
          const isPlaying = isCurrent && (status === "playing" || status === "buffering");

          return (
            <button
              key={track.filename}
              onClick={() => onPlayTrack(index)}
              className={`flex w-full items-center gap-3 rounded px-2 py-1.5 text-left transition-colors hover:bg-white/5 ${
                isCurrent ? "bg-white/5" : ""
              }`}
            >
              {/* Track number / playing indicator */}
              <span className="w-5 flex-shrink-0 text-right text-xs tabular-nums">
                {isPlaying ? (
                  <span className="inline-flex gap-0.5 text-deadly-highlight">
                    <span className="inline-block h-2.5 w-0.5 animate-pulse bg-current" />
                    <span className="inline-block h-3 w-0.5 animate-pulse bg-current [animation-delay:150ms]" />
                    <span className="inline-block h-2 w-0.5 animate-pulse bg-current [animation-delay:300ms]" />
                  </span>
                ) : (
                  <span className={isCurrent ? "text-deadly-highlight" : "text-white/30"}>
                    {track.track}
                  </span>
                )}
              </span>

              {/* Title */}
              <span
                className={`min-w-0 flex-1 truncate text-sm ${
                  isCurrent ? "font-medium text-deadly-highlight" : "text-white/80"
                }`}
              >
                {track.title}
              </span>

              {/* Duration */}
              <span className="flex-shrink-0 text-xs tabular-nums text-white/30">
                {formatDuration(track.duration)}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
