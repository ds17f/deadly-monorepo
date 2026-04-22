"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import MetricCard from "./MetricCard";
import { emojiForId } from "./emojiId";

interface ShowListener {
  show_id: string;
  iid: string;
  tracks_played: number[];
  last_seen: string;
  resumed: boolean;
}

interface PlaybackData {
  active_listeners: number;
  unique_shows: number;
  resumed_count: number;
  listeners: ShowListener[];
}

interface ShowName {
  id: string;
  d: string;
  v: string;
  c: string;
  s: string;
  tc: number;
}

type SortKey = "last_seen" | "progress";
type SortDir = "asc" | "desc";

function formatDate(d: string): string {
  const date = new Date(d + "T00:00:00");
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

function formatLastSeen(iso: string): string {
  const date = new Date(iso + "Z");
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric" }) +
    " " + date.toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" });
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
      if (sortKey === "progress") {
        const av = a.tracks_played.length;
        const bv = b.tracks_played.length;
        return sortDir === "asc" ? av - bv : bv - av;
      }
      const cmp = a.last_seen.localeCompare(b.last_seen);
      return sortDir === "asc" ? cmp : -cmp;
    });
    return sorted;
  }, [data, sortKey, sortDir, filterIid, excludeIids]);

  if (!data) return null;

  const arrow = (key: SortKey) =>
    sortKey === key ? (sortDir === "asc" ? " ▲" : " ▼") : "";

  function TrackBar({ listener }: { listener: ShowListener }) {
    const show = showMap.get(listener.show_id);
    const played = new Set(listener.tracks_played.map((t) => t > 0 ? t - 1 : t));
    const total = show?.tc || (played.size > 0 ? Math.max(...played) + 1 : 0);
    if (total === 0) return <span className="text-zinc-500 text-xs">{played.size} track{played.size !== 1 ? "s" : ""}</span>;

    return (
      <div className="flex items-center gap-2">
        <div className="flex gap-px" title={`${played.size} of ${total} tracks`}>
          {Array.from({ length: total }, (_, i) => (
            <div
              key={i}
              className={`h-3 rounded-sm ${played.has(i) ? "bg-deadly-blue" : "bg-zinc-700"}`}
              style={{ width: `${Math.max(Math.min(120 / total, 8), 2)}px` }}
            />
          ))}
        </div>
        <span className="text-xs text-zinc-500 whitespace-nowrap">{played.size}/{total}</span>
        {listener.resumed && (
          <span className="text-xs text-green-400 flex-shrink-0">resumed</span>
        )}
      </div>
    );
  }

  return (
    <>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-4">
        <MetricCard label="Active Listeners" value={data.active_listeners} />
        <MetricCard label="Unique Shows" value={data.unique_shows} />
        <MetricCard label="Resumed" value={data.resumed_count} />
      </div>

      {data.listeners.length > 0 && (
        <div className="bg-deadly-surface rounded-lg overflow-hidden">
          <button
            onClick={() => setExpanded(!expanded)}
            className="w-full px-4 py-3 flex items-center justify-between text-sm text-zinc-400 hover:text-zinc-200 transition-colors"
          >
            <span>Listening Activity ({filteredAndSorted.length}{filterIid || excludeIids.size > 0 ? ` of ${data.listeners.length}` : ""})</span>
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
                      Last Listened{arrow("last_seen")}
                    </th>
                    <th
                      className="px-4 py-2 text-left text-zinc-400 cursor-pointer hover:text-zinc-200 select-none"
                      onClick={() => toggleSort("progress")}
                    >
                      Progress{arrow("progress")}
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
                        <td className="px-4 py-2">
                          <TrackBar listener={l} />
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
                        <span className="text-xs text-zinc-400">{formatLastSeen(l.last_seen)}</span>
                      </div>
                      <p className="text-sm text-zinc-200 mb-1">
                        {show ? `${formatDate(show.d)} — ${show.v}` : l.show_id.slice(0, 40)}
                      </p>
                      <TrackBar listener={l} />
                    </div>
                  );
                })}
                {filteredAndSorted.length === 0 && (
                  <p className="text-center text-zinc-500 py-4">No matching activity</p>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </>
  );
}
