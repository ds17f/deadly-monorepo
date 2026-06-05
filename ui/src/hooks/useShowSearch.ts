"use client";

/**
 * Shared show-search state: the lazy-loaded client index, a debounced query,
 * and the resulting hits. Used by both the desktop top-bar dropdown
 * (short debounce) and the full-screen mobile search screen (longer debounce,
 * since typing on a phone keyboard is slower). Each consumer owns its own
 * instance — they're never on screen at the same time.
 */

import { useCallback, useEffect, useState } from "react";
import { loadSearchIndex, searchShows, type ShowSearchHit } from "@/lib/searchClient";

export function useShowSearch(debounceMs: number) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<ShowSearchHit[]>([]);
  const [total, setTotal] = useState(0);

  // Warm the index (e.g. on focus / on open) so the first keystroke is instant.
  const warm = useCallback(() => {
    loadSearchIndex().catch(() => {});
  }, []);

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
          }
        })
        .catch(() => {});
    }, debounceMs);
    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [query, debounceMs]);

  const reset = useCallback(() => {
    setQuery("");
    setResults([]);
    setTotal(0);
  }, []);

  return { query, setQuery, results, total, warm, reset };
}
