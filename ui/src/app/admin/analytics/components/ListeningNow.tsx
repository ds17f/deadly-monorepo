"use client";

import { useEffect, useState } from "react";

interface LiveListener {
  iid: string;
  platform: string;
  app_version: string;
  started_at: number;
  show_id: string | null;
  recording_id: string | null;
  track_index: number | null;
  source: string | null;
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

export default function ListeningNow() {
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
          className="flex items-baseline gap-3 bg-deadly-surface rounded-lg p-2 text-sm"
        >
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
      ))}
    </div>
  );
}
