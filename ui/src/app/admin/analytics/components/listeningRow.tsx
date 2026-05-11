"use client";

import { emojiForId } from "./emojiId";
import { useWatchedInstalls } from "./WatchedInstallsContext";

export type TrackOutcome = "complete" | "skipped" | "error" | "partial";

export interface TrackPlay {
  index: number;
  outcome: TrackOutcome;
}

const OUTCOME_COLOR: Record<TrackOutcome, string> = {
  complete: "bg-emerald-500",
  skipped: "bg-amber-400",
  error: "bg-red-500",
  partial: "bg-sky-400",
};

const OUTCOME_LABEL: Record<TrackOutcome, string> = {
  complete: "complete",
  skipped: "skipped",
  error: "error",
  partial: "partial",
};

const SEVERITY: Record<TrackOutcome, number> = {
  partial: 0,
  complete: 1,
  skipped: 2,
  error: 3,
};

export const SOURCE_LABELS: Record<string, string> = {
  auto_advance: "auto-advance",
  restore: "restore",
  browse: "show detail",
  library_favorites: "favorites",
  deeplink: "deep link",
  search_result: "search",
};

export function relativeAge(ts: number): string {
  const ageMs = Date.now() - ts;
  if (ageMs < 60_000) return `${Math.floor(ageMs / 1000)}s ago`;
  if (ageMs < 3_600_000) return `${Math.floor(ageMs / 60_000)}m ago`;
  if (ageMs < 86_400_000) return `${Math.floor(ageMs / 3_600_000)}h ago`;
  return `${Math.floor(ageMs / 86_400_000)}d ago`;
}

export function formatShowDate(showId: string | null): string {
  if (!showId) return "—";
  const m = showId.match(/^(\d{4}-\d{2}-\d{2})/);
  return m ? m[1] : showId.slice(0, 24);
}

function ShowDateLink({
  showId,
  className,
}: {
  showId: string | null;
  className?: string;
}) {
  const label = formatShowDate(showId);
  if (!showId) return <span className={className}>{label}</span>;
  return (
    <a
      href={`/shows/${showId}`}
      target="_blank"
      rel="noopener noreferrer"
      onClick={(e) => e.stopPropagation()}
      className={`${className ?? ""} hover:text-deadly-blue hover:underline decoration-dotted underline-offset-2 transition-colors`}
    >
      {label}
    </a>
  );
}

/**
 * Same bar shape as the Show Listening panel: each slot is one of the
 * show's actual tracks (when total track count is known) so an unplayed
 * track 8 of 12 reads as four trailing dim slots, not a missing tail.
 * Falls back to length-of-events when track count is unavailable.
 */
export function TrackOutcomeBar({
  tracks,
  totalTracks,
  compact = false,
}: {
  tracks: TrackPlay[];
  totalTracks: number | undefined;
  compact?: boolean;
}) {
  if (tracks.length === 0 && !totalTracks) return null;
  const outcomeByPos = new Map<number, TrackOutcome>();
  for (const t of tracks) {
    const pos = t.index > 0 ? t.index - 1 : t.index;
    const prior = outcomeByPos.get(pos);
    if (!prior || SEVERITY[t.outcome] > SEVERITY[prior]) {
      outcomeByPos.set(pos, t.outcome);
    }
  }
  const heard = outcomeByPos.size;
  const maxPos = heard > 0 ? Math.max(...outcomeByPos.keys()) + 1 : 0;
  const total = totalTracks && totalTracks > 0 ? totalTracks : maxPos;
  if (total === 0) return null;

  const slotMax = compact ? 6 : 8;
  const slotMin = 2;
  const target = compact ? 96 : 120;

  return (
    <div className="flex items-center gap-2 min-w-0">
      <div
        className="flex gap-px min-w-0"
        title={`${heard} of ${total} tracks`}
      >
        {Array.from({ length: total }, (_, i) => {
          const outcome = outcomeByPos.get(i);
          const cls = outcome ? OUTCOME_COLOR[outcome] : "bg-zinc-700";
          const label = outcome
            ? `track ${i + 1}: ${OUTCOME_LABEL[outcome]}`
            : `track ${i + 1}: not played`;
          return (
            <div
              key={i}
              title={label}
              className={`h-3 rounded-sm ${cls}`}
              style={{
                width: `${Math.max(Math.min(target / total, slotMax), slotMin)}px`,
              }}
            />
          );
        })}
      </div>
      <span className="text-xs text-zinc-500 whitespace-nowrap">
        {heard}/{total}
      </span>
    </div>
  );
}

export interface ListeningRowProps {
  iid: string;
  platform: string;
  app_version: string;
  ts: number;
  show_id: string | null;
  track_index: number | null;
  source: string | null;
  tracks: TrackPlay[];
  totalTracks: number | undefined;
  rowKey: string;
  onClick?: () => void;
}

/**
 * Shared row used by Listening Now and Recent Listening. Renders a
 * desktop single-row layout (sm+) and a stacked two-line card on mobile.
 */
export function ListeningRow({
  iid,
  platform,
  app_version,
  ts,
  show_id,
  track_index,
  source,
  tracks,
  totalTracks,
  onClick,
}: ListeningRowProps) {
  const interactive = !!onClick;
  const sourceLabel = source ? (SOURCE_LABELS[source] ?? source) : "—";
  const age = relativeAge(ts);
  const trackLabel = track_index != null ? `track ${track_index}` : "—";
  const { isWatched, nameFor, setWatched, unwatch } = useWatchedInstalls();
  const watched = isWatched(iid);
  const friendlyName = nameFor(iid);

  const toggleWatch = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (watched) unwatch(iid);
    else setWatched(iid, null, null);
  };

  const StarButton = (
    <button
      onClick={toggleWatch}
      title={watched ? "Stop watching" : "Watch this install"}
      className={`shrink-0 w-4 text-center leading-none transition-colors ${
        watched
          ? "text-amber-400 hover:text-amber-300"
          : "text-zinc-600 hover:text-amber-300"
      }`}
      aria-label={watched ? "Unwatch" : "Watch"}
    >
      {watched ? "★" : "☆"}
    </button>
  );

  return (
    <div
      onClick={onClick}
      className={`bg-deadly-surface rounded-lg text-sm ${
        interactive ? "cursor-pointer hover:bg-zinc-800 transition-colors" : ""
      } ${watched ? "ring-1 ring-amber-500/40" : ""}`}
    >
      {/* Mobile: stacked two-line card */}
      <div className="sm:hidden px-3 py-2 flex flex-col gap-1.5">
        <div className="flex items-center gap-2 min-w-0">
          <span
            className="inline-flex items-center gap-1 min-w-0 flex-1"
            title={iid}
          >
            {StarButton}
            <span className="shrink-0">{emojiForId(iid)}</span>
            {friendlyName ? (
              <span className="text-zinc-200 truncate min-w-0">
                {friendlyName}
              </span>
            ) : (
              <span className="font-mono text-xs text-zinc-400 shrink-0">
                {iid.slice(0, 8)}
              </span>
            )}
          </span>
          <ShowDateLink showId={show_id} className="text-zinc-300 shrink-0" />
          <span className="font-mono text-xs text-zinc-500 shrink-0 tabular-nums">
            {age}
          </span>
        </div>
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-xs text-zinc-500 truncate shrink-0">
            {sourceLabel} · {trackLabel}
          </span>
          <div className="ml-auto min-w-0">
            <TrackOutcomeBar tracks={tracks} totalTracks={totalTracks} compact />
          </div>
        </div>
      </div>

      {/* Desktop: single horizontal row */}
      <div className="hidden sm:flex items-center gap-3 px-2 py-1.5">
        <span
          className="inline-flex items-center gap-1 shrink-0 w-40"
          title={iid}
        >
          {StarButton}
          <span className="shrink-0">{emojiForId(iid)}</span>
          {friendlyName ? (
            <span className="text-zinc-200 truncate min-w-0">
              {friendlyName}
            </span>
          ) : (
            <span className="font-mono text-xs text-zinc-400 shrink-0">
              {iid.slice(0, 8)}
            </span>
          )}
        </span>
        <span className="font-mono text-xs text-zinc-500 w-20 shrink-0 tabular-nums">
          {age}
        </span>
        <ShowDateLink showId={show_id} className="text-zinc-300 w-24 shrink-0" />
        <span className="text-zinc-500 w-14 shrink-0">{trackLabel}</span>
        <span className="text-zinc-500 w-24 shrink-0 truncate">
          {sourceLabel}
        </span>
        <TrackOutcomeBar tracks={tracks} totalTracks={totalTracks} />
        <span className="text-xs text-zinc-600 ml-auto truncate shrink-0">
          {platform} {app_version}
        </span>
      </div>
    </div>
  );
}
