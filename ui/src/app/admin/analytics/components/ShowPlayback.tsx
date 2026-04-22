"use client";

import { useCallback, useEffect, useState } from "react";
import MetricCard from "./MetricCard";
import { emojiForId } from "./emojiId";

interface ShowListener {
  show_id: string;
  iid: string;
  sessions: number;
  tracks_played: number;
  deepest_track: number;
  completion_pct: number | null;
}

interface PlaybackData {
  active_listeners: number;
  unique_shows: number;
  avg_tracks_per_show: number;
  avg_show_completion: number | null;
  resume_rate: number | null;
  listeners: ShowListener[];
}

interface ShowName {
  id: string;
  d: string;
  v: string;
  c: string;
  s: string;
}

function formatDate(d: string): string {
  const date = new Date(d + "T00:00:00");
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

export default function ShowPlayback({ showMap }: { showMap: Map<string, ShowName> }) {
  const [data, setData] = useState<PlaybackData | null>(null);
  const [expanded, setExpanded] = useState(false);

  const fetch_ = useCallback(async () => {
    try {
      const res = await fetch("/api/analytics/playback?days=30", { credentials: "include" });
      if (res.ok) setData(await res.json());
    } catch {
      // non-critical
    }
  }, []);

  useEffect(() => {
    fetch_();
    const interval = setInterval(fetch_, 30_000);
    return () => clearInterval(interval);
  }, [fetch_]);

  if (!data) return null;

  return (
    <section className="mb-6">
      <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3">
        Show Listening (30d)
      </h2>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 mb-4">
        <MetricCard label="Active Listeners" value={data.active_listeners} />
        <MetricCard label="Unique Shows" value={data.unique_shows} />
        <MetricCard
          label="Avg Tracks/Show"
          value={data.avg_tracks_per_show}
        />
        <MetricCard
          label="Resume Rate"
          value={data.resume_rate !== null ? `${data.resume_rate}%` : "—"}
        />
      </div>

      {data.avg_show_completion !== null && (
        <div className="bg-deadly-surface rounded-lg p-4 mb-4">
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm text-zinc-400">Avg Show Completion</span>
            <span className="text-sm font-bold text-white">{data.avg_show_completion}%</span>
          </div>
          <div className="bg-zinc-800 rounded-full h-3 overflow-hidden">
            <div
              className="bg-deadly-blue h-full rounded-full transition-all"
              style={{ width: `${Math.min(data.avg_show_completion, 100)}%` }}
            />
          </div>
        </div>
      )}

      {/* Listener activity list */}
      {data.listeners.length > 0 && (
        <div className="bg-deadly-surface rounded-lg overflow-hidden">
          <button
            onClick={() => setExpanded(!expanded)}
            className="w-full px-4 py-3 flex items-center justify-between text-sm text-zinc-400 hover:text-zinc-200 transition-colors"
          >
            <span>Listening Sessions ({data.listeners.length})</span>
            <span>{expanded ? "▲" : "▼"}</span>
          </button>

          {expanded && (
            <div className="border-t border-zinc-700">
              {/* Desktop table */}
              <table className="w-full text-sm hidden lg:table">
                <thead>
                  <tr className="border-b border-zinc-700">
                    <th className="px-4 py-2 text-left text-zinc-400">Listener</th>
                    <th className="px-4 py-2 text-left text-zinc-400">Show</th>
                    <th className="px-4 py-2 text-right text-zinc-400">Sessions</th>
                    <th className="px-4 py-2 text-right text-zinc-400">Tracks</th>
                    <th className="px-4 py-2 text-right text-zinc-400">Deepest</th>
                    <th className="px-4 py-2 text-right text-zinc-400">Completion</th>
                  </tr>
                </thead>
                <tbody>
                  {data.listeners.map((l, i) => {
                    const show = showMap.get(l.show_id);
                    return (
                      <tr key={i} className="border-b border-zinc-800 last:border-0 hover:bg-zinc-800/50">
                        <td className="px-4 py-2">
                          <span className="mr-1">{emojiForId(l.iid)}</span>
                          <span className="font-mono text-xs text-zinc-500">{l.iid.slice(0, 8)}</span>
                        </td>
                        <td className="px-4 py-2 max-w-[300px]">
                          {show ? (
                            <span className="text-zinc-200">
                              {formatDate(show.d)} <span className="text-zinc-500">&mdash; {show.v}</span>
                            </span>
                          ) : (
                            <span className="text-zinc-400 font-mono text-xs">{l.show_id.slice(0, 30)}</span>
                          )}
                        </td>
                        <td className="px-4 py-2 text-right text-zinc-300 tabular-nums">
                          {l.sessions > 1 ? (
                            <span className="text-green-400">{l.sessions}</span>
                          ) : (
                            l.sessions
                          )}
                        </td>
                        <td className="px-4 py-2 text-right text-zinc-300 tabular-nums">{l.tracks_played}</td>
                        <td className="px-4 py-2 text-right text-zinc-400 tabular-nums">#{l.deepest_track}</td>
                        <td className="px-4 py-2 text-right tabular-nums">
                          {l.completion_pct !== null ? (
                            <span className={l.completion_pct >= 80 ? "text-green-400" : l.completion_pct >= 40 ? "text-yellow-400" : "text-zinc-400"}>
                              {l.completion_pct}%
                            </span>
                          ) : (
                            <span className="text-zinc-600">&mdash;</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>

              {/* Mobile card list */}
              <div className="lg:hidden p-3 space-y-2">
                {data.listeners.map((l, i) => {
                  const show = showMap.get(l.show_id);
                  return (
                    <div key={i} className="bg-zinc-800/50 rounded-lg p-3">
                      <div className="flex items-center justify-between mb-1">
                        <span>
                          <span className="mr-1">{emojiForId(l.iid)}</span>
                          <span className="font-mono text-xs text-zinc-500">{l.iid.slice(0, 8)}</span>
                        </span>
                        {l.completion_pct !== null && (
                          <span className={`text-xs font-medium ${l.completion_pct >= 80 ? "text-green-400" : l.completion_pct >= 40 ? "text-yellow-400" : "text-zinc-400"}`}>
                            {l.completion_pct}%
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-zinc-200 mb-1">
                        {show ? `${formatDate(show.d)} — ${show.v}` : l.show_id.slice(0, 40)}
                      </p>
                      <div className="flex items-center gap-3 text-xs text-zinc-500">
                        <span>{l.tracks_played} track{l.tracks_played !== 1 ? "s" : ""}</span>
                        <span>deepest #{l.deepest_track}</span>
                        {l.sessions > 1 && (
                          <span className="text-green-400">{l.sessions} sessions (resumed)</span>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
