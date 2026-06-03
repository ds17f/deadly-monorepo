"use client";

/**
 * Global shell search. The top-bar box queries the client search index
 * (lazy-loaded on first focus) and shows a live results dropdown — song,
 * member, venue, location, and date search, matching the mobile app.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { loadSearchIndex, searchShows, type ShowSearchHit } from "@/lib/searchClient";

function formatDate(d: string): string {
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

export default function SearchBox() {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<ShowSearchHit[]>([]);
  const [total, setTotal] = useState(0);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const boxRef = useRef<HTMLDivElement>(null);

  // Warm the index on first focus so the first keystroke is instant.
  const warm = useCallback(() => {
    loadSearchIndex().catch(() => {});
  }, []);

  // Debounced query.
  useEffect(() => {
    const q = query.trim();
    if (q.length < 2) {
      setResults([]);
      setTotal(0);
      return;
    }
    let cancelled = false;
    const t = setTimeout(() => {
      searchShows(q)
        .then(({ hits, total }) => {
          if (!cancelled) {
            setResults(hits);
            setTotal(total);
            setActive(0);
          }
        })
        .catch(() => {});
    }, 140);
    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [query]);

  // Close on outside click.
  useEffect(() => {
    function onDown(e: MouseEvent) {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, []);

  function go(hit: ShowSearchHit) {
    setOpen(false);
    setQuery("");
    setResults([]);
    router.push(`/shows/${hit.showId}`);
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (!open || results.length === 0) return;
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive((i) => (i + 1) % results.length);
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((i) => (i - 1 + results.length) % results.length);
    } else if (e.key === "Enter") {
      e.preventDefault();
      go(results[active]);
    } else if (e.key === "Escape") {
      setOpen(false);
    }
  }

  const showDropdown = open && query.trim().length >= 2;

  return (
    <div
      ref={boxRef}
      // Mobile: grow to fill the top bar (sides shrink to logo/avatar).
      // Desktop: fixed max-w-md, centered by the flex-1 header spacers.
      className="relative min-w-0 w-full max-w-md flex-1 sm:flex-none"
    >
      <div className="flex items-center gap-2 rounded-full bg-deadly-surface px-4 py-2">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" className="flex-shrink-0 text-white/40">
          <path d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 10-.7.7l.27.28v.79l5 4.99L20.49 19zm-6 0A4.5 4.5 0 1114 9.5 4.5 4.5 0 019.5 14z" />
        </svg>
        <input
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => {
            warm();
            setOpen(true);
          }}
          onKeyDown={onKeyDown}
          placeholder="Search shows by date, venue, city, song, or member"
          aria-label="Search shows"
          className="w-full bg-transparent text-sm text-white placeholder:text-white/40 focus:outline-none"
        />
      </div>

      {showDropdown && (
        <div className="absolute left-0 right-0 top-full z-30 mt-2 overflow-hidden rounded-lg border border-white/10 bg-deadly-bg shadow-xl">
          {results.length === 0 ? (
            <p className="px-4 py-3 text-sm text-white/40">No shows found.</p>
          ) : (
            <>
              <div className="max-h-[60vh] overflow-y-auto py-1">
                {results.map((hit, i) => (
                  <button
                    key={hit.showId}
                    onClick={() => go(hit)}
                    onMouseEnter={() => setActive(i)}
                    className={`flex w-full items-center gap-3 px-4 py-2 text-left transition ${
                      i === active ? "bg-white/10" : "hover:bg-white/5"
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
                ))}
              </div>
              {total > results.length && (
                <p className="border-t border-white/10 px-4 py-2 text-xs text-white/40">
                  Showing {results.length} of {total.toLocaleString()} — keep typing to narrow
                </p>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
