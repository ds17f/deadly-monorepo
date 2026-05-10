"use client";

import { useEffect, useState } from "react";

type TrackOutcome = "complete" | "skipped" | "error" | "partial";

interface TrackPlay {
  index: number;
  outcome: TrackOutcome;
}

interface LiveListener {
  iid: string;
  platform: string;
  app_version: string;
  started_at: number;
  show_id: string | null;
  recording_id: string | null;
  track_index: number | null;
  source: string | null;
  tracks: TrackPlay[];
}

interface ShowName {
  id: string;
  d: string;
  v: string;
  c: string;
  s: string;
  tc: number;
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

/**
 * Same bar shape as the Show Listening panel: each slot is one of the
 * show's actual tracks (when total track count is known) so an unplayed
 * track 8 of 12 reads as four trailing dim slots, not a missing tail.
 * Falls back to length-of-events when track count is unavailable.
 */
function TrackOutcomeBar({
  tracks,
  totalTracks,
}: {
  tracks: TrackPlay[];
  totalTracks: number | undefined;
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

  return (
    <div className="flex items-center gap-2 mt-1">
      <div className="flex gap-px" title={`${heard} of ${total} tracks`}>
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
              style={{ width: `${Math.max(Math.min(120 / total, 8), 2)}px` }}
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

const POLL_INTERVAL_MS = 15_000;

const SOURCE_LABELS: Record<string, string> = {
  auto_advance: "auto-advance",
  restore: "restore",
  browse: "show detail",
  library_favorites: "favorites",
  deeplink: "deep link",
  search_result: "search",
};

function relativeAge(startedAt: number): string {
  const ageMs = Date.now() - startedAt;
  if (ageMs < 60_000) return `${Math.floor(ageMs / 1000)}s ago`;
  return `${Math.floor(ageMs / 60_000)}m ago`;
}

function formatShowDate(showId: string | null): string {
  if (!showId) return "—";
  const m = showId.match(/^(\d{4}-\d{2}-\d{2})/);
  return m ? m[1] : showId.slice(0, 24);
}

export default function ListeningNow({
  showMap,
}: {
  showMap?: Map<string, ShowName>;
} = {}) {
  const [listeners, setListeners] = useState<LiveListener[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const fetchData = async () => {
    try {
      const res = await fetch("/api/analytics/live", { credentials: "include" });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const body = (await res.json()) as { listeners: LiveListener[] };
      setListeners(body.listeners);
      setError(null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Failed to load");
    }
  };

  useEffect(() => {
    fetchData();
    const id = setInterval(fetchData, POLL_INTERVAL_MS);
    return () => clearInterval(id);
  }, []);

  if (error)
    return <p className="text-sm text-red-400">Listening Now error: {error}</p>;
  if (listeners === null)
    return <p className="text-sm text-zinc-500">Loading…</p>;

  if (listeners.length === 0) {
    return (
      <p className="text-sm text-zinc-500 italic">
        No active sessions in the last 5 minutes.
      </p>
    );
  }

  return (
    <div className="space-y-1">
      <p className="text-xs text-zinc-500 mb-2">
        {listeners.length} active session{listeners.length !== 1 ? "s" : ""} ·
        window covers the longest Dead jams; killed sessions ghost up to 45 min
      </p>
      {listeners.map((l) => (
        <div
          key={`${l.iid}-${l.started_at}`}
          className="bg-deadly-surface rounded-lg p-2 text-sm"
        >
          <div className="flex items-baseline gap-3">
            <span className="font-mono text-xs text-zinc-500 w-20 shrink-0 tabular-nums">
              {relativeAge(l.started_at)}
            </span>
            <span className="text-zinc-300 w-24 shrink-0">
              {formatShowDate(l.show_id)}
            </span>
            <span className="text-zinc-500 w-14 shrink-0">
              {l.track_index != null ? `track ${l.track_index}` : "—"}
            </span>
            <span className="text-zinc-500 w-24 shrink-0 truncate">
              {l.source ? (SOURCE_LABELS[l.source] ?? l.source) : "—"}
            </span>
            <span className="text-xs text-zinc-600 ml-auto truncate">
              {l.platform} {l.app_version}
            </span>
          </div>
          <TrackOutcomeBar
            tracks={l.tracks}
            totalTracks={l.show_id ? showMap?.get(l.show_id)?.tc : undefined}
          />
        </div>
      ))}
    </div>
  );
}
