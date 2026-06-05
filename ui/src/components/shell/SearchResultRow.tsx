"use client";

/**
 * Presentational bits shared by the desktop search dropdown and the mobile
 * full-screen search screen: the magnifier icon, the date formatter, and a
 * single result row.
 */

import type { ShowSearchHit } from "@/lib/searchClient";

export function SearchIcon({ className = "text-white/40" }: { className?: string }) {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="currentColor"
      className={`flex-shrink-0 ${className}`}
      aria-hidden
    >
      <path d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 10-.7.7l.27.28v.79l5 4.99L20.49 19zm-6 0A4.5 4.5 0 1114 9.5 4.5 4.5 0 019.5 14z" />
    </svg>
  );
}

export function formatDate(d: string): string {
  const [y, m, day] = d.split("-").map(Number);
  if (!y) return d;
  return new Date(y, (m ?? 1) - 1, day ?? 1).toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

// Only label the non-obvious matches (a venue/location/date result speaks for
// itself; a song or member match deserves a "why this showed up" hint).
const HINT: Partial<Record<ShowSearchHit["matchType"], string>> = {
  song: "Setlist",
  member: "Lineup",
};

export function SearchResultRow({
  hit,
  active = false,
  onSelect,
  onMouseEnter,
}: {
  hit: ShowSearchHit;
  active?: boolean;
  onSelect: () => void;
  onMouseEnter?: () => void;
}) {
  return (
    <button
      onClick={onSelect}
      onMouseEnter={onMouseEnter}
      className={`flex w-full items-center gap-3 px-4 py-3 text-left transition ${
        active ? "bg-white/10" : "hover:bg-white/5"
      }`}
    >
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-semibold text-white">
          {formatDate(hit.date)}
        </span>
        <span className="block truncate text-xs text-white/50">
          {hit.venue}
          {hit.city ? ` · ${hit.city}${hit.state ? `, ${hit.state}` : ""}` : ""}
        </span>
      </span>
      {HINT[hit.matchType] && (
        <span className="flex-shrink-0 rounded-full bg-white/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-white/60">
          {HINT[hit.matchType]}
        </span>
      )}
    </button>
  );
}
