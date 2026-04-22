"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import MetricCard from "./MetricCard";
import { emojiForId } from "./emojiId";

interface ShowListener {
  show_id: string;
  iid: string;
  sessions: number;
  tracks_played: number;
  deepest_track: number;
  completion_pct: number | null;
  last_seen: string;
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

type SortKey = "last_seen" | "sessions" | "tracks_played" | "deepest_track" | "completion_pct";
type SortDir = "asc" | "desc";

function formatDate(d: string): string {
  const date = new Date(d + "T00:00:00");
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

function formatLastSeen(iso: string): string {
  const ms = Date.now() - new Date(iso + "Z").getTime();
  if (ms < 60_000) return "just now";
  if (ms < 3600_000) return `${Math.floor(ms / 60_000)}m ago`;
  if (ms < 86400_000) return `${Math.floor(ms / 3600_000)}h ago`;
  return `${Math.floor(ms / 86400_000)}d ago`;
}

export default function ShowPlayback({ showMap }: { showMap: Map<string, ShowName> }) {
  const [data, setData] = useState<PlaybackData | null>(null);
  const [expanded, setExpanded] = useState(true);
  const [sortKey, setSortKey] = useState<SortKey>("last_seen");
  const [sortDir, setSortDir] = useState<SortDir>("desc");
  const [filterIid, setFilterIid] = useState<string | null>(null);
  const [excludeIids, setExcludeIids] = useState<Set<string>>(new Set());

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

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("desc");
    }
  };

  const filteredAndSorted = useMemo(() => {
    if (!data) return [];
    let list = data.listeners;
    if (filterIid) {
      list = list.filter((l) => l.iid === filterIid);
    }
    if (excludeIids.size > 0) {
      list = list.filter((l) => !excludeIids.has(l.iid));
    }
    const sorted = [...list];
    sorted.sort((a, b) => {
      const av = a[sortKey];
      const bv = b[sortKey];
      if (av === null && bv === null) return 0;
      if (av === null) return 1;
      if (bv === null) return -1;
      if (typeof av === "number" && typeof bv === "number") {
        return sortDir === "asc" ? av - bv : bv - av;
      }
      const cmp = String(av).localeCompare(String(bv));
      return sortDir === "asc" ? cmp : -cmp;
    });
    return sorted;
  }, [data, sortKey, sortDir, filterIid, excludeIids]);

  if (!data) return null;

  const arrow = (key: SortKey) =>
    sortKey === key ? (sortDir === "asc" ? " ▲" : " ▼") : "";

  return (
    <>
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
            <span>Listening Sessions ({filteredAndSorted.length}{filterIid || excludeIids.size > 0 ? ` of ${data.listeners.length}` : ""})</span>
            <span>{expanded ? "▲" : "▼"}</span>
          </button>

          {expanded && (
            <div className="border-t border-zinc-700">
              {/* Filter controls */}
              {(filterIid || excludeIids.size > 0) && (
                <div className="px-4 py-2 flex items-center gap-2 flex-wrap border-b border-zinc-700">
                  {filterIid && (
                    <span className="inline-flex items-center gap-1 text-xs bg-deadly-blue/20 text-deadly-blue rounded-full px-2 py-0.5">
                      {emojiForId(filterIid)} {filterIid.slice(0, 8)}
                      <button onClick={() => setFilterIid(null)} className="hover:text-white">&times;</button>
                    </span>
                  )}
                  {Array.from(excludeIids).map((iid) => (
                    <span key={iid} className="inline-flex items-center gap-1 text-xs bg-red-500/20 text-red-400 rounded-full px-2 py-0.5">
                      {emojiForId(iid)} {iid.slice(0, 8)}
                      <button
                        onClick={() => setExcludeIids((s) => { const n = new Set(s); n.delete(iid); return n; })}
                        className="hover:text-white"
                      >&times;</button>
                    </span>
                  ))}
                  <button
                    onClick={() => { setFilterIid(null); setExcludeIids(new Set()); }}
                    className="text-xs text-zinc-500 hover:text-zinc-300"
                  >
                    clear all
                  </button>
                </div>
              )}

              {/* Desktop table */}
              <table className="w-full text-sm hidden lg:table">
                <thead>
                  <tr className="border-b border-zinc-700">
                    <th className="px-4 py-2 text-left text-zinc-400">Listener</th>
                    <th className="px-4 py-2 text-left text-zinc-400">Show</th>
                    <th
                      className="px-4 py-2 text-right text-zinc-400 cursor-pointer hover:text-zinc-200 select-none"
                      onClick={() => toggleSort("last_seen")}
                    >
                      Last Seen{arrow("last_seen")}
                    </th>
                    <th
                      className="px-4 py-2 text-right text-zinc-400 cursor-pointer hover:text-zinc-200 select-none"
                      onClick={() => toggleSort("sessions")}
                    >
                      Sessions{arrow("sessions")}
                    </th>
                    <th
                      className="px-4 py-2 text-right text-zinc-400 cursor-pointer hover:text-zinc-200 select-none"
                      onClick={() => toggleSort("tracks_played")}
                    >
                      Tracks{arrow("tracks_played")}
                    </th>
                    <th
                      className="px-4 py-2 text-right text-zinc-400 cursor-pointer hover:text-zinc-200 select-none"
                      onClick={() => toggleSort("deepest_track")}
                    >
                      Deepest{arrow("deepest_track")}
                    </th>
                    <th
                      className="px-4 py-2 text-right text-zinc-400 cursor-pointer hover:text-zinc-200 select-none"
                      onClick={() => toggleSort("completion_pct")}
                    >
                      Completion{arrow("completion_pct")}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {filteredAndSorted.map((l, i) => {
                    const show = showMap.get(l.show_id);
                    return (
                      <tr key={i} className="border-b border-zinc-800 last:border-0 hover:bg-zinc-800/50 group">
                        <td className="px-4 py-2 relative">
                          <span className="mr-1">{emojiForId(l.iid)}</span>
                          <span className="font-mono text-xs text-zinc-500">{l.iid.slice(0, 8)}</span>
                          <span className="absolute right-1 top-1/2 -translate-y-1/2 hidden group-hover:inline-flex items-center gap-1 bg-zinc-800 rounded px-1 py-0.5">
                            <button
                              onClick={() => setFilterIid(l.iid)}
                              className="text-xs text-zinc-500 hover:text-deadly-blue"
                              title="Filter to this user"
                            >
                              only
                            </button>
                            <button
                              onClick={() => setExcludeIids((s) => new Set(s).add(l.iid))}
                              className="text-xs text-zinc-500 hover:text-red-400"
                              title="Exclude this user"
                            >
                              hide
                            </button>
                          </span>
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
                        <td className="px-4 py-2 text-right text-zinc-400 tabular-nums whitespace-nowrap">
                          {formatLastSeen(l.last_seen)}
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
                {filteredAndSorted.map((l, i) => {
                  const show = showMap.get(l.show_id);
                  return (
                    <div key={i} className="bg-zinc-800/50 rounded-lg p-3">
                      <div className="flex items-center justify-between mb-1">
                        <span className="flex items-center gap-1">
                          <span className="mr-1">{emojiForId(l.iid)}</span>
                          <span className="font-mono text-xs text-zinc-500">{l.iid.slice(0, 8)}</span>
                          <button
                            onClick={() => setFilterIid(l.iid)}
                            className="text-xs text-zinc-600 hover:text-deadly-blue ml-1"
                          >
                            only
                          </button>
                          <button
                            onClick={() => setExcludeIids((s) => new Set(s).add(l.iid))}
                            className="text-xs text-zinc-600 hover:text-red-400"
                          >
                            hide
                          </button>
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
                        <span>{formatLastSeen(l.last_seen)}</span>
                        <span>{l.tracks_played} track{l.tracks_played !== 1 ? "s" : ""}</span>
                        <span>deepest #{l.deepest_track}</span>
                        {l.sessions > 1 && (
                          <span className="text-green-400">{l.sessions} sessions</span>
                        )}
                      </div>
                    </div>
                  );
                })}
                {filteredAndSorted.length === 0 && (
                  <p className="text-center text-zinc-500 py-4">No matching sessions</p>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </>
  );
}
