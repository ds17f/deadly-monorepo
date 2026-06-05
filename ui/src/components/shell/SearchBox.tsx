"use client";

/**
 * Global shell search.
 *
 *   Desktop (sm+): an inline top-bar box with a live results dropdown.
 *   Mobile (< sm): a trigger that opens a full-screen search screen
 *                  (MobileSearchOverlay) — a dropdown can't coexist with the
 *                  on-screen keyboard.
 *
 * Both query the same lazy-loaded client index via useShowSearch.
 */

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import * as analytics from "@/lib/analytics";
import { useShowSearch } from "@/hooks/useShowSearch";
import { SearchIcon, SearchResultRow } from "./SearchResultRow";
import MobileSearchOverlay from "./MobileSearchOverlay";
import type { ShowSearchHit } from "@/lib/searchClient";

export default function SearchBox() {
  const router = useRouter();
  const { query, setQuery, results, total, warm, reset } = useShowSearch(160);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [mobileOpen, setMobileOpen] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);

  // Reset the keyboard-highlight whenever the result set changes.
  useEffect(() => {
    setActive(0);
  }, [results]);

  // Close the desktop dropdown on outside click.
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
    const q = query.trim();
    analytics.track("search", {
      query: q,
      query_length: q.length,
      result_count: total,
      selected_index: results.indexOf(hit),
    });
    setOpen(false);
    reset();
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
    <>
      {/* Mobile: a trigger that opens the full-screen search screen. */}
      <button
        type="button"
        onClick={() => {
          warm();
          setMobileOpen(true);
        }}
        aria-label="Search shows"
        className="flex w-full min-w-0 flex-1 items-center gap-2 rounded-full bg-deadly-surface px-4 py-2 text-left sm:hidden"
      >
        <SearchIcon />
        <span className="truncate text-sm text-white/40">Search shows</span>
      </button>

      {/* Desktop: inline box with a live dropdown. */}
      <div
        ref={boxRef}
        className="relative hidden w-full max-w-md min-w-0 flex-none sm:block"
      >
        <div className="flex items-center gap-2 rounded-full bg-deadly-surface px-4 py-2">
          <SearchIcon />
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
            type="search"
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            className="w-full bg-transparent text-sm text-white placeholder:text-white/40 focus:outline-none"
          />
        </div>

        {showDropdown && (
          <div className="absolute right-0 left-0 top-full z-30 mt-2 overflow-hidden rounded-lg border border-white/10 bg-deadly-bg shadow-xl">
            {results.length === 0 ? (
              <p className="px-4 py-3 text-sm text-white/40">No shows found.</p>
            ) : (
              <>
                <div className="max-h-[60vh] overflow-y-auto py-1">
                  {results.map((hit, i) => (
                    <SearchResultRow
                      key={hit.showId}
                      hit={hit}
                      active={i === active}
                      onSelect={() => go(hit)}
                      onMouseEnter={() => setActive(i)}
                    />
                  ))}
                </div>
                {total > results.length && (
                  <p className="border-t border-white/10 px-4 py-2 text-xs text-white/40">
                    Showing {results.length} of {total.toLocaleString()} — keep typing to
                    narrow
                  </p>
                )}
              </>
            )}
          </div>
        )}
      </div>

      {mobileOpen && <MobileSearchOverlay onClose={() => setMobileOpen(false)} />}
    </>
  );
}
