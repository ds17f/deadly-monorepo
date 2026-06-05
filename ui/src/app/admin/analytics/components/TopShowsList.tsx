"use client";

import { useEffect, useState } from "react";
import { usePlatformFilter } from "./PlatformFilterContext";

interface ShowInfo {
  id: string;
  d: string;
  v: string;
  c: string;
  s: string;
  img: string | null;
}

interface TopShow {
  show_id: string;
  listeners: number;
  track_plays: number;
  completion_rate: number | null;
}

interface TopShowsListProps {
  showMap: Map<string, ShowInfo>;
  onShowClick?: (showId: string) => void;
}

const RANGES: Array<{ label: string; days: number }> = [
  { label: "1d", days: 1 },
  { label: "7d", days: 7 },
  { label: "14d", days: 14 },
  { label: "30d", days: 30 },
  { label: "60d", days: 60 },
  { label: "180d", days: 180 },
  { label: "1y", days: 365 },
];

function formatDate(d: string): string {
  const date = new Date(d + "T00:00:00");
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

export default function TopShowsList({ showMap, onShowClick }: TopShowsListProps) {
  const { withParam, param } = usePlatformFilter();
  const [days, setDays] = useState(30);
  const [shows, setShows] = useState<TopShow[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setShows(null);
    fetch(withParam(`/api/analytics/top-shows?days=${days}`), { credentials: "include" })
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const body = (await res.json()) as { shows: TopShow[] };
        setShows(body.shows);
        setError(null);
      })
      .catch((e: unknown) =>
        setError(e instanceof Error ? e.message : "Failed to load"),
      );
  }, [days, withParam, param]);

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-1 flex-wrap">
        {RANGES.map((r) => (
          <button
            key={r.days}
            onClick={() => setDays(r.days)}
            className={`text-xs px-2 py-0.5 rounded transition-colors ${
              days === r.days
                ? "bg-deadly-blue text-white"
                : "bg-zinc-800 text-zinc-400 hover:bg-zinc-700 hover:text-zinc-200"
            }`}
          >
            {r.label}
          </button>
        ))}
      </div>

      {error && <p className="text-sm text-red-400">Top Shows error: {error}</p>}
      {!error && shows === null && (
        <p className="text-sm text-zinc-500">Loading…</p>
      )}
      {!error && shows && shows.length === 0 && (
        <p className="text-sm text-zinc-500 italic">No qualifying listens in this window.</p>
      )}
      {!error && shows && shows.length > 0 && (
        <div className="space-y-1">
          {shows.map((show, i) => {
            const info = showMap.get(show.show_id);
            return (
              <div
                key={show.show_id}
                className="bg-deadly-surface rounded-lg p-2 flex items-center gap-3 hover:bg-zinc-700/50 transition-colors cursor-pointer"
                onClick={(e) => {
                  if (onShowClick) {
                    e.stopPropagation();
                    onShowClick(show.show_id);
                  }
                }}
              >
                <span className="text-zinc-500 text-sm font-mono w-5 text-right shrink-0 tabular-nums">
                  {i + 1}
                </span>
                {info?.img ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={info.img}
                    alt=""
                    loading="lazy"
                    className="w-10 h-10 object-cover rounded shrink-0 bg-zinc-800"
                  />
                ) : (
                  <div className="w-10 h-10 rounded bg-zinc-800 shrink-0" />
                )}
                <a
                  href={`/shows/${show.show_id}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  onClick={(e) => e.stopPropagation()}
                  className="min-w-0 flex-1 hover:text-deadly-blue transition-colors"
                >
                  {info ? (
                    <>
                      <p className="text-sm text-zinc-200">{formatDate(info.d)}</p>
                      <p className="text-xs text-zinc-400 truncate">
                        {info.v} &mdash; {info.c}, {info.s}
                      </p>
                    </>
                  ) : (
                    <p className="text-sm text-zinc-300 font-mono break-all">{show.show_id}</p>
                  )}
                </a>
                <div className="flex flex-col items-end shrink-0 tabular-nums">
                  <span className="text-sm text-zinc-300">
                    {show.listeners} listener{show.listeners !== 1 ? "s" : ""}
                  </span>
                  <span className="text-xs text-zinc-500">
                    {show.track_plays} track{show.track_plays !== 1 ? "s" : ""} played
                  </span>
                  <span
                    className="text-xs text-zinc-500"
                    title="Completion: total listened / total duration across all playback_end events for this show (≥5 plays required)"
                  >
                    {show.completion_rate != null
                      ? `${Math.round(show.completion_rate * 100)}% completed`
                      : <span className="text-zinc-600">— completion</span>}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
