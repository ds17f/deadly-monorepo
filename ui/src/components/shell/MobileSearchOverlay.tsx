"use client";

/**
 * Full-screen mobile search — the phone counterpart to the desktop top-bar
 * dropdown, modeled on the native app's search screen. A top-bar dropdown
 * fights the on-screen keyboard (the list ends up behind it); a dedicated
 * screen doesn't: the input pins to the top, results fill everything above the
 * keyboard, and tapping a result navigates and closes.
 *
 * The panel is sized to the VISUAL viewport (which shrinks when the keyboard
 * opens on iOS), so the results area always ends exactly at the keyboard's top
 * edge and scrolls within that space. Longer debounce than desktop — phone
 * typing is slower, so we wait a beat before firing each query.
 */

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import * as analytics from "@/lib/analytics";
import { useShowSearch } from "@/hooks/useShowSearch";
import { SearchIcon, SearchResultRow } from "./SearchResultRow";
import type { ShowSearchHit } from "@/lib/searchClient";

export default function MobileSearchOverlay({ onClose }: { onClose: () => void }) {
  const router = useRouter();
  const { query, setQuery, results, total, warm, reset } = useShowSearch(350);
  const inputRef = useRef<HTMLInputElement>(null);
  const [vv, setVv] = useState<{ top: number; height: number } | null>(null);

  // Warm the index and focus the field as soon as the screen opens.
  useEffect(() => {
    warm();
    inputRef.current?.focus();
  }, [warm]);

  // Track the visual viewport so the panel sits exactly above the keyboard.
  useEffect(() => {
    const v = typeof window !== "undefined" ? window.visualViewport : null;
    if (!v) return;
    const update = () => setVv({ top: v.offsetTop, height: v.height });
    update();
    v.addEventListener("resize", update);
    v.addEventListener("scroll", update);
    return () => {
      v.removeEventListener("resize", update);
      v.removeEventListener("scroll", update);
    };
  }, []);

  // Escape (hardware/bluetooth keyboards) closes the screen.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function close() {
    reset();
    onClose();
  }

  function go(hit: ShowSearchHit) {
    const q = query.trim();
    analytics.track("search", {
      query: q,
      query_length: q.length,
      result_count: total,
      selected_index: results.indexOf(hit),
    });
    close();
    router.push(`/shows/${hit.showId}`);
  }

  const trimmed = query.trim();

  return (
    <div
      className="fixed inset-x-0 top-0 z-[70] flex flex-col bg-deadly-bg sm:hidden"
      style={vv ? { top: vv.top, height: vv.height } : { height: "100dvh" }}
    >
      {/* search field + cancel */}
      <div className="flex flex-shrink-0 items-center gap-2 px-3 py-2">
        <div className="flex min-w-0 flex-1 items-center gap-2 rounded-full bg-deadly-surface px-4 py-2.5">
          <SearchIcon />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Shows, songs, venues, members…"
            aria-label="Search shows"
            type="search"
            enterKeyHint="search"
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            // 16px keeps iOS from auto-zooming the field on focus.
            className="w-full bg-transparent text-base text-white placeholder:text-white/40 focus:outline-none"
          />
        </div>
        <button
          onClick={close}
          className="flex-shrink-0 px-2 py-1 text-sm font-medium text-white/70 transition hover:text-white"
        >
          Cancel
        </button>
      </div>

      {/* results — owns the remaining height above the keyboard, scrolls */}
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
        {trimmed.length < 2 ? (
          <p className="px-4 py-6 text-sm text-white/40">
            Search 2,300+ shows by date, venue, city, song, or member.
          </p>
        ) : results.length === 0 ? (
          <p className="px-4 py-6 text-sm text-white/40">No shows found.</p>
        ) : (
          <>
            {results.map((hit) => (
              <SearchResultRow key={hit.showId} hit={hit} onSelect={() => go(hit)} />
            ))}
            {total > results.length && (
              <p className="px-4 py-3 text-xs text-white/40">
                Showing {results.length} of {total.toLocaleString()} — keep typing to
                narrow
              </p>
            )}
          </>
        )}
      </div>
    </div>
  );
}
