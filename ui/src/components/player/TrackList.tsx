import type { ArchiveTrack, PlaybackStatus } from "@/types/player";
import { useUserData } from "@/contexts/UserDataContext";

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
  // When present, each row gets a favorite-song heart toggle keyed on
  // (showId, trackTitle). Omit on surfaces with no show context.
  showId?: string | null;
  recordingId?: string | null;
  // Fill the available height (flex child) instead of capping at max-h-80 —
  // used by the fullscreen player's side rail.
  fill?: boolean;
}

// Heart toggle for a single track. Always rendered for favorited tracks (so
// the state reads at a glance); otherwise it only appears on row hover/focus —
// this keeps dense track lists from looking busy while still affording a
// direct per-track toggle (mirrors the iOS passive indicator + player toggle).
function FavoriteHeart({
  showId,
  trackTitle,
  trackNumber,
  recordingId,
}: {
  showId: string;
  trackTitle: string;
  trackNumber: number;
  recordingId?: string | null;
}) {
  const { isFavoriteTrack, toggleFavoriteTrack } = useUserData();
  const favorited = isFavoriteTrack(showId, trackTitle);

  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation();
        toggleFavoriteTrack({
          showId,
          trackTitle,
          trackNumber: trackNumber > 0 ? trackNumber : null,
          recordingId: recordingId ?? null,
        });
      }}
      aria-label={favorited ? "Remove from favorite songs" : "Add to favorite songs"}
      aria-pressed={favorited}
      className={`flex-shrink-0 rounded p-1 transition-opacity hover:text-deadly-highlight focus:opacity-100 focus-visible:outline-none ${
        favorited
          ? "text-deadly-highlight opacity-100"
          : "text-white/40 opacity-0 group-hover:opacity-100"
      }`}
    >
      <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true"
        fill={favorited ? "currentColor" : "none"} stroke="currentColor" strokeWidth="2">
        <path d="M12 21s-7.5-4.9-10-9.2C.3 8.3 2 5 5.2 5c1.9 0 3.3 1 3.8 2.4C9.5 6 11 5 12.8 5 16 5 17.7 8.3 16 11.8 13.5 16.1 12 21 12 21z" />
      </svg>
    </button>
  );
}

export default function TrackList({
  tracks,
  isLoading,
  currentTrackIndex,
  status,
  onPlayTrack,
  showId,
  recordingId,
  fill = false,
}: TrackListProps) {
  if (isLoading) {
    return (
      <div className={fill ? "space-y-2" : "mt-4 space-y-2"}>
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
    <div className={fill ? "flex min-h-0 flex-1 flex-col" : "mt-4"}>
      <h4 className="mb-2 text-sm font-bold text-deadly-title">Tracks</h4>
      <div className={fill ? "min-h-0 flex-1 overflow-y-auto" : "max-h-80 overflow-y-auto"}>
        {tracks.map((track, index) => {
          const isCurrent = index === currentTrackIndex;
          const isPlaying = isCurrent && (status === "playing" || status === "buffering");

          return (
            <div
              key={track.filename}
              className={`group flex w-full items-center gap-3 rounded px-2 py-1.5 transition-colors hover:bg-white/5 ${
                isCurrent ? "bg-white/5" : ""
              }`}
            >
              <button
                onClick={() => onPlayTrack(index)}
                className="flex min-w-0 flex-1 items-center gap-3 text-left"
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
              </button>

              {/* Favorite toggle — only when we know the show context */}
              {showId && (
                <FavoriteHeart
                  showId={showId}
                  trackTitle={track.title}
                  trackNumber={track.track}
                  recordingId={recordingId}
                />
              )}

              {/* Duration */}
              <span className="flex-shrink-0 text-xs tabular-nums text-white/30">
                {formatDuration(track.duration)}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
