"use client";

import { useEffect, useMemo, useState } from "react";
import { emojiForId } from "./emojiId";

const SHOW_ID_RE = /^\d{4}-\d{2}-\d{2}$/;

function isShowId(value: string): boolean {
  return SHOW_ID_RE.test(value);
}

function ShowLink({ id, className }: { id: string; className?: string }) {
  return (
    <a
      href={`/shows/${id}`}
      target="_blank"
      rel="noopener noreferrer"
      onClick={(e) => e.stopPropagation()}
      className={
        className ??
        "text-deadly-blue hover:text-white underline decoration-dotted underline-offset-2 transition-colors"
      }
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

type SortDir = "asc" | "desc";

interface DetailPanelProps {
  title: string;
  filter?: string;
  rows: DetailRow[];
  loading: boolean;
  onClose: () => void;
  onClearFilter?: () => void;
  onOpenInstall: (iid: string) => void;
}

function relativeTime(iso: string): string {
  const ms = Date.now() - new Date(iso + "Z").getTime();
  if (ms < 60_000) return "just now";
  if (ms < 3600_000) return `${Math.floor(ms / 60_000)}m ago`;
  if (ms < 86400_000) return `${Math.floor(ms / 3600_000)}h ago`;
  return `${Math.floor(ms / 86400_000)}d ago`;
}

export default function DetailPanel({
  title,
  filter,
  rows,
  loading,
  onClose,
  onClearFilter,
  onOpenInstall,
}: DetailPanelProps) {
  const [sortKey, setSortKey] = useState<keyof DetailRow>("last_seen");
  const [sortDir, setSortDir] = useState<SortDir>("desc");

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

  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [onClose]);

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
            <h2 className="text-base font-semibold text-white truncate">
              {title}
              {filter && (
                <span className="text-sm font-normal text-zinc-400 ml-2">
                  &mdash; {filter}
                </span>
              )}
            </h2>
          </div>
          <div className="flex items-center gap-3 flex-shrink-0">
            {filter && onClearFilter && (
              <button
                onClick={onClearFilter}
                className="text-xs text-zinc-400 hover:text-white transition-colors"
              >
                clear
              </button>
            )}
            <span className="text-xs text-zinc-500">{rows.length} rows</span>
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
          {loading ? (
            <div className="flex items-center justify-center p-12">
              <p className="text-zinc-400">Loading...</p>
            </div>
          ) : sortedRows.length === 0 ? (
            <div className="flex items-center justify-center p-12">
              <p className="text-zinc-500">No data</p>
            </div>
          ) : (
            <>
              {/* Sort controls */}
              <div className="flex items-center gap-2 px-3 pt-3 pb-1 text-xs text-zinc-500">
                <span>Sort</span>
                <select
                  value={sortKey}
                  onChange={(e) => setSortKey(e.target.value as keyof DetailRow)}
                  className="bg-zinc-800 text-zinc-300 rounded px-2 py-1 border border-zinc-700"
                >
                  {sortedRows.some((r) => r.detail != null) && (
                    <option value="detail">Detail</option>
                  )}
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

              {/* Card list */}
              <div className="p-3 space-y-2">
                {sortedRows.map((row, i) => {
                  const detail = row.detail;
                  const detailIsShow = detail != null && isShowId(detail);
                  return (
                    <div
                      key={i}
                      className="bg-zinc-800/50 rounded-lg p-3 hover:bg-zinc-800 active:bg-zinc-700/50 cursor-pointer"
                      onClick={() => onOpenInstall(row.iid)}
                    >
                      {detail && (
                        <div className="flex items-baseline justify-between gap-3 mb-1.5">
                          <span className="text-sm text-zinc-100 break-all">
                            {detailIsShow ? (
                              <ShowLink id={detail} />
                            ) : (
                              detail
                            )}
                          </span>
                          <span className="text-sm text-zinc-300 tabular-nums flex-shrink-0">
                            {row.event_count}
                          </span>
                        </div>
                      )}
                      <div className="flex items-center justify-between gap-2 text-xs">
                        <button
                          className="text-deadly-blue hover:text-white transition-colors flex items-center gap-1 min-w-0"
                          onClick={(e) => {
                            e.stopPropagation();
                            onOpenInstall(row.iid);
                          }}
                        >
                          <span>{emojiForId(row.iid)}</span>
                          <span className="font-mono">{row.iid?.slice(0, 8)}</span>
                        </button>
                        <div className="flex items-center gap-2 text-zinc-400 flex-shrink-0">
                          <span>{row.platform}</span>
                          <span className="text-zinc-500">v{row.app_version}</span>
                          <span className="text-zinc-500">{relativeTime(row.last_seen)}</span>
                          {!detail && (
                            <span className="text-zinc-300 tabular-nums ml-1">
                              {row.event_count}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </>
          )}
        </div>
      </div>
    </>
  );
}
