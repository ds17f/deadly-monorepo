"use client";

import { useEffect, useRef, useState } from "react";
import DecadeCascadeFilter, {
  type FilterNode,
} from "./DecadeCascadeFilter";
import type { SortDir, SortSpec } from "./libraryItem";

// Custom sort dropdown — replaces the native <select> so it matches the app's
// dark menu styling instead of the OS look.
function SortDropdown({
  sorts,
  sortId,
  onSortId,
}: {
  sorts: SortSpec[];
  sortId: string;
  onSortId: (id: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const current = sorts.find((s) => s.id === sortId) ?? sorts[0];

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        aria-label="Sort by"
        className="flex items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-3.5 py-1.5 text-sm font-medium text-white transition hover:border-white/25"
      >
        {current.label}
        <svg
          width="14"
          height="14"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.5"
          className={`text-white/50 transition ${open ? "rotate-180" : ""}`}
        >
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>

      {open && (
        <div className="absolute left-0 top-full z-50 mt-1 w-44 rounded-lg border border-white/10 bg-deadly-surface p-1.5 shadow-xl">
          {sorts.map((s) => (
            <button
              key={s.id}
              onClick={() => {
                onSortId(s.id);
                setOpen(false);
              }}
              className={`flex w-full items-center justify-between rounded-md px-2.5 py-1.5 text-left text-sm transition hover:bg-white/10 ${
                s.id === sortId ? "text-white" : "text-white/70"
              }`}
            >
              {s.label}
              {s.id === sortId && <span className="text-deadly-accent">✓</span>}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

// Search · sort · direction · count · list/grid, with the shared decade→year
// cascade underneath. Presentational — LibraryView owns all the state.
export default function LibraryToolbar({
  query,
  onQuery,
  sorts,
  sortId,
  onSortId,
  dir,
  onDir,
  view,
  onView,
  count,
  path,
  onPath,
}: {
  query: string;
  onQuery: (s: string) => void;
  sorts: SortSpec[];
  sortId: string;
  onSortId: (id: string) => void;
  dir: SortDir;
  onDir: (d: SortDir) => void;
  view: "list" | "grid";
  onView: (v: "list" | "grid") => void;
  count: number;
  path: FilterNode[];
  onPath: (p: FilterNode[]) => void;
}) {
  return (
    <div className="mb-4 space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <input
          value={query}
          onChange={(e) => onQuery(e.target.value)}
          placeholder="Search by date, venue, or city…"
          className="min-w-[180px] flex-1 rounded-full border border-white/10 bg-white/5 px-4 py-1.5 text-sm text-white placeholder-white/30 focus:border-deadly-accent focus:outline-none"
        />

        <SortDropdown sorts={sorts} sortId={sortId} onSortId={onSortId} />

        <button
          onClick={() => onDir(dir === "asc" ? "desc" : "asc")}
          aria-label={dir === "asc" ? "Ascending" : "Descending"}
          title={dir === "asc" ? "Ascending" : "Descending"}
          className="flex h-8 w-8 items-center justify-center rounded-full border border-white/10 bg-white/5 text-white/70 transition hover:text-white"
        >
          {dir === "asc" ? "↑" : "↓"}
        </button>

        <span className="px-1 text-sm text-white/40">{count}</span>

        <div className="flex overflow-hidden rounded-full border border-white/10">
          <button
            onClick={() => onView("list")}
            aria-label="List view"
            aria-pressed={view === "list"}
            className={`px-2.5 py-1.5 text-sm transition ${
              view === "list" ? "bg-white/15 text-white" : "text-white/50 hover:text-white"
            }`}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M3 5h18v2H3zM3 11h18v2H3zM3 17h18v2H3z" />
            </svg>
          </button>
          <button
            onClick={() => onView("grid")}
            aria-label="Grid view"
            aria-pressed={view === "grid"}
            className={`px-2.5 py-1.5 text-sm transition ${
              view === "grid" ? "bg-white/15 text-white" : "text-white/50 hover:text-white"
            }`}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M3 3h8v8H3zM13 3h8v8h-8zM3 13h8v8H3zM13 13h8v8h-8z" />
            </svg>
          </button>
        </div>
      </div>

      <DecadeCascadeFilter path={path} onChange={onPath} />
    </div>
  );
}
