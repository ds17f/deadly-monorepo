"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { emojiForId } from "./emojiId";
import { useWatchedInstalls } from "./WatchedInstallsContext";

const SHOW_ID_RE = /^\d{4}-\d{2}-\d{2}(-[\w-]+)?$/;
function isShowId(value: string): boolean {
  return SHOW_ID_RE.test(value);
}

function ShowLink({ id }: { id: string }) {
  return (
    <a
      href={`/shows/${id}`}
      target="_blank"
      rel="noopener noreferrer"
      onClick={(e) => e.stopPropagation()}
      className="text-deadly-blue hover:text-white underline decoration-dotted underline-offset-2 [overflow-wrap:anywhere]"
    >
      {id}
    </a>
  );
}

interface DetailRow {
  iid: string;
  platform: string;
  app_version: string;
  last_seen: string;
  event_count: number;
  detail?: string;
}

type SortKey = keyof DetailRow;
type SortDir = "asc" | "desc";

const METRIC_LABELS: Record<string, string> = {
  dau: "Daily Active Users",
  wau: "Weekly Active Users",
  mau: "Monthly Active Users",
  total_installs: "Total Installs",
  stale_installs: "Stale Installs (30d)",
  events_today: "Events Today",
  top_shows: "Most-listened shows (30d)",
  feature_adoption: "Feature Adoption (30d)",
  platform_split: "Active installs by platform (30d)",
  playback: "Playback (30d)",
  playback_source: "Plays by Source (30d)",
  new_installs: "New Installs",
};

function relativeTime(iso: string): string {
  const ms = Date.now() - new Date(iso + "Z").getTime();
  if (ms < 60_000) return "just now";
  if (ms < 3600_000) return `${Math.floor(ms / 60_000)}m`;
  if (ms < 86400_000) return `${Math.floor(ms / 3600_000)}h`;
  return `${Math.floor(ms / 86400_000)}d`;
}

interface Props {
  metric: string;
  filter?: string;
  backHref: string;
}

export default function MetricDetailView({ metric, filter, backHref }: Props) {
  const router = useRouter();
  const { isWatched, nameFor, setWatched, unwatch } = useWatchedInstalls();
  const [rows, setRows] = useState<DetailRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [sortKey, setSortKey] = useState<SortKey>("last_seen");
  const [sortDir, setSortDir] = useState<SortDir>("desc");

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [metric, filter]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    const params = new URLSearchParams({ metric });
    if (filter) params.set("filter", filter);
    fetch(`/api/analytics/detail?${params}`, { credentials: "include" })
      .then((r) => (r.ok ? r.json() : []))
      .then((data: DetailRow[]) => {
        if (!cancelled) setRows(data);
      })
      .catch(() => {
        if (!cancelled) setRows([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [metric, filter]);

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

  const title = METRIC_LABELS[metric] ?? metric;
  const hasDetail = sortedRows.some((r) => r.detail != null);

  return (
    <div className="min-h-screen bg-deadly-bg">
      <div className="max-w-3xl mx-auto p-4 sm:p-6">
        {/* Header */}
        <div className="mb-3 flex items-center gap-3">
          <button
            onClick={() => router.push(backHref)}
            className="text-zinc-400 hover:text-white text-sm flex items-center gap-1"
          >
            <span>&larr;</span> Back
          </button>
        </div>
        <div className="mb-3 flex items-baseline justify-between gap-2 flex-wrap">
          <h1 className="text-lg font-semibold text-white">
            {title}
            {filter && (
              <span className="text-sm font-normal text-zinc-400 ml-2 [overflow-wrap:anywhere]">
                &mdash; {filter}
              </span>
            )}
          </h1>
          <div className="flex items-center gap-3">
            {filter && (
              <button
                onClick={() => router.push(`/admin/analytics?metric=${metric}`)}
                className="text-xs text-zinc-400 hover:text-white"
              >
                clear filter
              </button>
            )}
            <span className="text-xs text-zinc-500">{rows.length} rows</span>
          </div>
        </div>

        {loading ? (
          <p className="text-zinc-400 py-8 text-center">Loading…</p>
        ) : sortedRows.length === 0 ? (
          <p className="text-zinc-500 py-8 text-center">No data</p>
        ) : (
          <>
            {/* Sort controls */}
            <div className="flex items-center gap-2 mb-2 text-xs text-zinc-500">
              <span>Sort</span>
              <select
                value={sortKey}
                onChange={(e) => setSortKey(e.target.value as SortKey)}
                className="bg-zinc-800 text-zinc-300 rounded px-2 py-1 border border-zinc-700"
              >
                {hasDetail && <option value="detail">Detail</option>}
                <option value="iid">Install</option>
                <option value="platform">Platform</option>
                <option value="app_version">Version</option>
                <option value="last_seen">Last seen</option>
                <option value="event_count">Count</option>
              </select>
              <button
                onClick={() => setSortDir((d) => (d === "asc" ? "desc" : "asc"))}
                className="text-zinc-300 hover:text-white px-2 py-1 rounded border border-zinc-700 bg-zinc-800"
                aria-label="Toggle sort direction"
              >
                {sortDir === "asc" ? "▲" : "▼"}
              </button>
            </div>

            {/* Compact rows */}
            <div className="space-y-1">
              {sortedRows.map((row, i) => {
                const detail = row.detail;
                const detailIsShow = detail != null && isShowId(detail);
                const rowWatched = isWatched(row.iid);
                return (
                  <div
                    key={i}
                    role="button"
                    tabIndex={0}
                    onClick={() =>
                      router.push(
                        `/admin/analytics?install=${encodeURIComponent(row.iid)}`,
                      )
                    }
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        router.push(
                          `/admin/analytics?install=${encodeURIComponent(row.iid)}`,
                        );
                      }
                    }}
                    className={`w-full text-left bg-zinc-800/50 hover:bg-zinc-800 active:bg-zinc-700/50 rounded px-2.5 py-1.5 flex flex-col gap-0.5 cursor-pointer ${
                      rowWatched ? "ring-1 ring-amber-500/40" : ""
                    }`}
                  >
                    {detail && (
                      <div className="flex items-baseline justify-between gap-2 min-w-0 text-sm">
                        <span className="text-zinc-100 min-w-0 flex-1 [overflow-wrap:anywhere]">
                          {detailIsShow ? <ShowLink id={detail} /> : detail}
                        </span>
                        <span className="text-zinc-300 tabular-nums flex-shrink-0">
                          {row.event_count}
                        </span>
                      </div>
                    )}
                    <div className="flex items-center justify-between gap-2 text-xs">
                      <span className="flex items-center gap-1 min-w-0 text-deadly-blue">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            if (rowWatched) unwatch(row.iid);
                            else setWatched(row.iid, null, null);
                          }}
                          title={rowWatched ? "Stop watching" : "Watch this install"}
                          aria-label={rowWatched ? "Unwatch" : "Watch"}
                          className={`shrink-0 w-4 text-center leading-none transition-colors ${
                            rowWatched
                              ? "text-amber-400 hover:text-amber-300"
                              : "text-zinc-600 hover:text-amber-300"
                          }`}
                        >
                          {rowWatched ? "★" : "☆"}
                        </button>
                        <span className="shrink-0">{emojiForId(row.iid)}</span>
                        {nameFor(row.iid) ? (
                          <span className="text-zinc-200 truncate min-w-0">
                            {nameFor(row.iid)}
                          </span>
                        ) : (
                          <span className="font-mono shrink-0">
                            {row.iid?.slice(0, 8)}
                          </span>
                        )}
                      </span>
                      <span className="flex items-center gap-1.5 text-zinc-500 flex-shrink-0">
                        <span>{row.platform}</span>
                        <span>v{row.app_version}</span>
                        <span>{relativeTime(row.last_seen)}</span>
                        {!detail && (
                          <span className="text-zinc-300 tabular-nums">
                            {row.event_count}
                          </span>
                        )}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
