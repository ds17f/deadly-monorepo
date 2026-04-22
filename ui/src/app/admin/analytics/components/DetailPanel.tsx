"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { emojiForId } from "./emojiId";

interface DetailRow {
  iid: string;
  platform: string;
  app_version: string;
  last_seen: string;
  event_count: number;
  detail?: string;
}

interface InstallEvent {
  id: number;
  event: string;
  ts: number;
  sid: string;
  platform: string;
  app_version: string;
  props: string | null;
}

interface InstallData {
  iid: string;
  platform: string;
  app_version: string;
  first_seen: string;
  last_seen: string;
  total_events: number;
  events: InstallEvent[];
}

type SortDir = "asc" | "desc";

interface DetailPanelProps {
  title: string;
  filter?: string;
  rows: DetailRow[];
  loading: boolean;
  onClose: () => void;
  onClearFilter?: () => void;
}

function relativeTime(iso: string): string {
  const ms = Date.now() - new Date(iso + "Z").getTime();
  if (ms < 60_000) return "just now";
  if (ms < 3600_000) return `${Math.floor(ms / 60_000)}m ago`;
  if (ms < 86400_000) return `${Math.floor(ms / 3600_000)}h ago`;
  return `${Math.floor(ms / 86400_000)}d ago`;
}

function formatProps(propsStr: string): string {
  try {
    const obj = JSON.parse(propsStr);
    return Object.entries(obj)
      .map(([k, v]) => `${k}=${v}`)
      .join(", ");
  } catch {
    return propsStr;
  }
}

export default function DetailPanel({
  title,
  filter,
  rows,
  loading,
  onClose,
  onClearFilter,
}: DetailPanelProps) {
  const [sortKey, setSortKey] = useState<keyof DetailRow>("last_seen");
  const [sortDir, setSortDir] = useState<SortDir>("desc");
  const [activeInstall, setActiveInstall] = useState<string | null>(null);
  const [installData, setInstallData] = useState<InstallData | null>(null);
  const [installLoading, setInstallLoading] = useState(false);
  const [installEventFilter, setInstallEventFilter] = useState<string | null>(null);

  const toggleSort = (key: keyof DetailRow) => {
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("desc");
    }
  };

  const sortedRows = useMemo(() => {
    const sorted = [...rows];
    sorted.sort((a, b) => {
      const aVal = a[sortKey] ?? "";
      const bVal = b[sortKey] ?? "";
      if (typeof aVal === "number" && typeof bVal === "number") {
        return sortDir === "asc" ? aVal - bVal : bVal - aVal;
      }
      const cmp = String(aVal).localeCompare(String(bVal));
      return sortDir === "asc" ? cmp : -cmp;
    });
    return sorted;
  }, [rows, sortKey, sortDir]);

  const fetchInstall = useCallback(async (iid: string) => {
    setInstallLoading(true);
    try {
      const res = await fetch(`/api/analytics/install/${encodeURIComponent(iid)}`, {
        credentials: "include",
      });
      if (res.ok) {
        setInstallData(await res.json());
      }
    } catch {
      // ignore
    } finally {
      setInstallLoading(false);
    }
  }, []);

  const openInstall = (iid: string) => {
    setActiveInstall(iid);
    setInstallEventFilter(null);
    fetchInstall(iid);
  };

  const backToList = () => {
    setActiveInstall(null);
    setInstallData(null);
  };

  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (activeInstall) backToList();
        else onClose();
      }
    };
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [activeInstall, onClose]);

  const filteredInstallEvents = useMemo(() => {
    if (!installData) return [];
    let events = installData.events;
    if (installEventFilter) {
      events = events.filter((e) => e.event === installEventFilter);
    }
    return events;
  }, [installData, installEventFilter]);

  const installEventTypes = useMemo(() => {
    if (!installData) return [];
    return Array.from(new Set(installData.events.map((e) => e.event))).sort();
  }, [installData]);

  return (
    <>
      {/* Backdrop */}
      <div className="fixed inset-0 bg-black/50 z-40" onClick={onClose} />

      {/* Panel — bottom sheet on mobile, side panel on desktop */}
      <div className="fixed z-50 bg-deadly-surface flex flex-col
        inset-x-0 bottom-0 h-[85vh] rounded-t-2xl
        lg:inset-x-auto lg:right-0 lg:top-0 lg:bottom-0 lg:h-full lg:w-[560px] lg:rounded-none lg:rounded-l-2xl
      ">
        {/* Drag indicator (mobile) */}
        <div className="lg:hidden flex justify-center py-2">
          <div className="w-10 h-1 bg-zinc-600 rounded-full" />
        </div>

        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-zinc-700 flex-shrink-0">
          <div className="flex items-center gap-2 min-w-0">
            {activeInstall && (
              <button
                onClick={backToList}
                className="text-sm text-zinc-400 hover:text-white transition-colors flex-shrink-0"
              >
                &larr;
              </button>
            )}
            <h2 className="text-base font-semibold text-white truncate">
              {activeInstall ? (
                <>
                  <span className="text-zinc-400">{title} &rsaquo; </span>
                  <span className="text-sm">{emojiForId(activeInstall)} <span className="font-mono text-zinc-500">{activeInstall.slice(0, 8)}</span></span>
                </>
              ) : (
                <>
                  {title}
                  {filter && (
                    <span className="text-sm font-normal text-zinc-400 ml-2">
                      &mdash; {filter}
                    </span>
                  )}
                </>
              )}
            </h2>
          </div>
          <div className="flex items-center gap-3 flex-shrink-0">
            {!activeInstall && filter && onClearFilter && (
              <button
                onClick={onClearFilter}
                className="text-xs text-zinc-400 hover:text-white transition-colors"
              >
                clear
              </button>
            )}
            {!activeInstall && (
              <span className="text-xs text-zinc-500">{rows.length} rows</span>
            )}
            <button
              onClick={onClose}
              className="text-zinc-400 hover:text-white text-xl leading-none"
            >
              &times;
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-auto">
          {activeInstall ? (
            // Install detail view
            installLoading && !installData ? (
              <div className="flex items-center justify-center p-12">
                <p className="text-zinc-400">Loading events...</p>
              </div>
            ) : installData ? (
              <div className="p-4">
                {/* Install metadata */}
                <div className="grid grid-cols-2 gap-3 mb-4">
                  <div>
                    <p className="text-xs text-zinc-500 uppercase tracking-wider">Platform</p>
                    <p className="text-sm text-white">{installData.platform}</p>
                  </div>
                  <div>
                    <p className="text-xs text-zinc-500 uppercase tracking-wider">Version</p>
                    <p className="text-sm text-white">{installData.app_version}</p>
                  </div>
                  <div>
                    <p className="text-xs text-zinc-500 uppercase tracking-wider">First Seen</p>
                    <p className="text-sm text-white">{installData.first_seen}</p>
                  </div>
                  <div>
                    <p className="text-xs text-zinc-500 uppercase tracking-wider">Last Seen</p>
                    <p className="text-sm text-white">{installData.last_seen}</p>
                  </div>
                </div>

                {/* Event filter */}
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs text-zinc-500">
                    {filteredInstallEvents.length} event{filteredInstallEvents.length !== 1 ? "s" : ""}
                  </span>
                  <select
                    value={installEventFilter ?? ""}
                    onChange={(e) => setInstallEventFilter(e.target.value || null)}
                    className="bg-zinc-800 text-xs text-zinc-300 rounded px-2 py-1 border border-zinc-700"
                  >
                    <option value="">All events</option>
                    {installEventTypes.map((t) => (
                      <option key={t} value={t}>{t}</option>
                    ))}
                  </select>
                </div>

                {/* Event list as cards */}
                <div className="space-y-2">
                  {filteredInstallEvents.map((evt) => (
                    <div key={evt.id} className="bg-zinc-800/50 rounded-lg p-3">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-sm text-zinc-200">{evt.event}</span>
                        <span className="text-xs text-zinc-500 font-mono">
                          {new Date(evt.ts).toISOString().replace("T", " ").slice(0, 19)}
                        </span>
                      </div>
                      <div className="flex items-center gap-3 text-xs text-zinc-500">
                        <span>{evt.platform}</span>
                        <span>{evt.app_version}</span>
                        <span className="font-mono">sid:{evt.sid.slice(0, 8)}</span>
                      </div>
                      {evt.props && (
                        <p className="text-xs text-zinc-600 font-mono mt-1 break-all">
                          {formatProps(evt.props)}
                        </p>
                      )}
                    </div>
                  ))}
                  {filteredInstallEvents.length === 0 && (
                    <p className="text-center text-zinc-500 py-8">No events</p>
                  )}
                </div>
              </div>
            ) : null
          ) : loading ? (
            <div className="flex items-center justify-center p-12">
              <p className="text-zinc-400">Loading...</p>
            </div>
          ) : sortedRows.length === 0 ? (
            <div className="flex items-center justify-center p-12">
              <p className="text-zinc-500">No data</p>
            </div>
          ) : (
            <>
              {/* Desktop table */}
              <table className="w-full text-sm hidden lg:table">
                <thead className="sticky top-0 bg-deadly-surface">
                  <tr className="border-b border-zinc-700">
                    {sortedRows.some((r) => r.detail != null) && (
                      <SortHeader label="Detail" sortKey="detail" current={sortKey} dir={sortDir} onClick={toggleSort} />
                    )}
                    <SortHeader label="Install ID" sortKey="iid" current={sortKey} dir={sortDir} onClick={toggleSort} />
                    <SortHeader label="Platform" sortKey="platform" current={sortKey} dir={sortDir} onClick={toggleSort} />
                    <SortHeader label="Version" sortKey="app_version" current={sortKey} dir={sortDir} onClick={toggleSort} />
                    <SortHeader label="Last Seen" sortKey="last_seen" current={sortKey} dir={sortDir} onClick={toggleSort} />
                    <SortHeader label="Count" sortKey="event_count" current={sortKey} dir={sortDir} onClick={toggleSort} align="right" />
                  </tr>
                </thead>
                <tbody>
                  {sortedRows.map((row, i) => (
                    <tr key={i} className="border-b border-zinc-800 last:border-0 hover:bg-zinc-800/50">
                      {sortedRows.some((r) => r.detail != null) && (
                        <td className="px-4 py-2 text-zinc-200 max-w-[200px] truncate">
                          {row.detail ?? "—"}
                        </td>
                      )}
                      <td className="px-4 py-2">
                        <button
                          className="text-deadly-blue hover:text-white transition-colors"
                          onClick={() => openInstall(row.iid)}
                        >
                          <span className="mr-1">{emojiForId(row.iid)}</span>
                          <span className="font-mono text-xs">{row.iid?.slice(0, 8)}</span>
                        </button>
                      </td>
                      <td className="px-4 py-2 text-zinc-300">{row.platform}</td>
                      <td className="px-4 py-2 text-zinc-400">{row.app_version}</td>
                      <td className="px-4 py-2 text-zinc-400">{row.last_seen}</td>
                      <td className="px-4 py-2 text-right text-zinc-300">{row.event_count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {/* Mobile card list */}
              <div className="lg:hidden p-3 space-y-2">
                {sortedRows.map((row, i) => (
                  <div
                    key={i}
                    className="bg-zinc-800/50 rounded-lg p-3 active:bg-zinc-700/50"
                    onClick={() => openInstall(row.iid)}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-deadly-blue">
                        <span className="mr-1">{emojiForId(row.iid)}</span>
                        <span className="font-mono text-xs">{row.iid?.slice(0, 8)}</span>
                      </span>
                      <span className="text-xs text-zinc-500">{relativeTime(row.last_seen)}</span>
                    </div>
                    <div className="flex items-center gap-2 text-xs">
                      <span className="text-zinc-300">{row.platform}</span>
                      <span className="text-zinc-500">v{row.app_version}</span>
                      {row.detail && (
                        <span className="text-zinc-400 truncate">{row.detail}</span>
                      )}
                      <span className="ml-auto text-zinc-400">{row.event_count}</span>
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      </div>
    </>
  );
}

function SortHeader({
  label,
  sortKey,
  current,
  dir,
  onClick,
  align,
}: {
  label: string;
  sortKey: keyof DetailRow;
  current: keyof DetailRow;
  dir: SortDir;
  onClick: (key: keyof DetailRow) => void;
  align?: "right";
}) {
  const active = current === sortKey;
  return (
    <th
      className={`px-4 py-2 text-zinc-400 cursor-pointer hover:text-zinc-200 select-none whitespace-nowrap ${
        align === "right" ? "text-right" : "text-left"
      }`}
      onClick={() => onClick(sortKey)}
    >
      {label}
      {active && <span className="ml-1">{dir === "asc" ? "▲" : "▼"}</span>}
    </th>
  );
}
