"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { ShowIndexEntry, CollectionSummary, YearBucket } from "@/types/homepage";
import { searchShows } from "@/lib/searchClient";
import HeroSection from "./HeroSection";
import HomeDiscovery from "./HomeDiscovery";
import HomeRightRail from "./HomeRightRail";
import YearTimeline from "./YearTimeline";
import SearchFilter, { type SortBy } from "./SearchFilter";
import ShowList from "./ShowList";
import ShowCarousel, { showToCarouselItem } from "./ShowCarousel";
import { RightRailSlot } from "@/components/shell/RightRail";

export default function HomeContent({
  showIndex,
  topRatedAll,
  collections,
  yearData,
  totalShows,
}: {
  showIndex: ShowIndexEntry[];
  topRatedAll: ShowIndexEntry[];
  collections: CollectionSummary[];
  yearData: YearBucket[];
  totalShows: number;
}) {
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedYear, setSelectedYear] = useState<number | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [sortBy, setSortBy] = useState<SortBy>("date");
  const [selectedDecade, setSelectedDecade] = useState<{
    from: number;
    to: number;
  } | null>(null);
  const [selectedSource, setSelectedSource] = useState<string | null>(null);
  const [includeNoRecordings, setIncludeNoRecordings] = useState(false);
  const browseRef = useRef<HTMLElement>(null);

  // Song/member-aware browse: for queries >=2 chars, resolve the matching
  // show_ids through the shared MiniSearch index (same engine as the global
  // top-bar search). null = not ready/applicable -> the substring fallback in
  // `filtered` runs instead, so the list never flashes empty while it loads.
  const [searchHits, setSearchHits] = useState<Set<string> | null>(null);
  useEffect(() => {
    const q = searchQuery.trim();
    if (q.length < 2) {
      setSearchHits(null);
      return;
    }
    let cancelled = false;
    searchShows(q, Number.MAX_SAFE_INTEGER)
      .then((res) => {
        if (!cancelled) setSearchHits(new Set(res.hits.map((h) => h.showId)));
      })
      .catch(() => {
        if (!cancelled) setSearchHits(null); // degrade to substring on failure
      });
    return () => {
      cancelled = true;
    };
  }, [searchQuery]);

  // Anchor the Browse-all section at the top of the pane whenever a search is
  // active — a literal, instant jump (not a smooth scroll), run from an effect
  // so it lands after the results render. `min-h-[100dvh]` on the section keeps
  // the column tall enough for the heading to reach the very top even at 0 hits.
  useEffect(() => {
    if (searchQuery.trim()) {
      browseRef.current?.scrollIntoView({ block: "start" });
    }
  }, [searchQuery, searchHits]);

  const filtered = useMemo(() => {
    let list = showIndex;

    // Hide shows without recordings by default
    if (!includeNoRecordings) {
      list = list.filter((s) => s.rc > 0);
    }

    // Year from timeline
    if (selectedYear !== null) {
      list = list.filter(
        (s) => parseInt(s.d.slice(0, 4), 10) === selectedYear
      );
    }

    // Decade chip
    if (selectedDecade !== null) {
      list = list.filter((s) => {
        const y = parseInt(s.d.slice(0, 4), 10);
        return y >= selectedDecade.from && y <= selectedDecade.to;
      });
    }

    // Source type chip
    if (selectedSource !== null) {
      list = list.filter((s) => s.st.includes(selectedSource));
    }

    // Text search. >=2 chars with a ready index -> song/member/venue/date
    // aware hit set; otherwise a cheap substring fallback (single char, index
    // still loading, or load failed) over venue/city/state/date.
    if (searchQuery.trim()) {
      const q = searchQuery.trim().toLowerCase();
      if (q.length >= 2 && searchHits) {
        list = list.filter((s) => searchHits.has(s.id));
      } else {
        list = list.filter(
          (s) =>
            s.v?.toLowerCase().includes(q) ||
            s.c?.toLowerCase().includes(q) ||
            s.s?.toLowerCase().includes(q) ||
            s.d.includes(q)
        );
      }
    }

    if (sortBy === "rating") {
      list = [...list].sort((a, b) => b.r - a.r || b.ar - a.ar);
    }

    return list;
  }, [showIndex, searchQuery, searchHits, selectedYear, selectedDecade, selectedSource, sortBy, includeNoRecordings]);

  const hasActiveFilter =
    selectedYear !== null ||
    selectedDecade !== null ||
    selectedSource !== null ||
    searchQuery.trim() !== "";

  const topRated = useMemo(() => {
    if (!hasActiveFilter) return topRatedAll;
    return [...filtered].sort((a, b) => b.r - a.r).slice(0, 20);
  }, [hasActiveFilter, topRatedAll, filtered]);

  const topRatedItems = useMemo(
    () =>
      topRated
        .slice(0, 15)
        .map((s) => showToCarouselItem(s, s.r > 0 ? `★ ${s.r.toFixed(1)}` : undefined)),
    [topRated],
  );

  function resetPage() {
    setCurrentPage(0);
  }

  function handleSearchChange(q: string) {
    setSearchQuery(q);
    resetPage();
  }

  function handleYearSelect(year: number | null) {
    setSelectedYear(year);
    // Clear decade chip when picking a specific year
    if (year !== null) setSelectedDecade(null);
    resetPage();
  }

  function handleSortChange(s: SortBy) {
    setSortBy(s);
    resetPage();
  }

  function handleDecadeChange(d: { from: number; to: number } | null) {
    setSelectedDecade(d);
    // Clear year timeline when picking a decade
    if (d !== null) setSelectedYear(null);
    resetPage();
  }

  function handleSourceChange(s: string | null) {
    setSelectedSource(s);
    resetPage();
  }

  return (
    <>
      {/* Conversion + context: get-the-app (+ QR) and now-playing. Leads
          above the content on mobile so the install CTA is up top. */}
      <RightRailSlot mobilePlacement="above">
        <HomeRightRail />
      </RightRailSlot>

      {/* What this site is. */}
      <HeroSection totalShows={totalShows} />

      {/* Discovery carousels — the mobile-app home, with room. */}
      <HomeDiscovery showIndex={showIndex} collections={collections} />

      {/* Browse all — the full catalog (the SEO surface) with search/filter. */}
      <section
        ref={browseRef}
        className={`mt-10 scroll-mt-4 border-t border-white/10 pt-8${
          // While searching, guarantee the section is tall enough to scroll its
          // heading to the top of the pane even on a short (or empty) result set.
          searchQuery.trim() ? " min-h-[100dvh]" : ""
        }`}
      >
        <h2 className="mb-4 text-lg font-bold text-white">
          Browse all {totalShows.toLocaleString()} shows
        </h2>
        <YearTimeline
          yearData={yearData}
          selectedYear={selectedYear}
          onSelectYear={handleYearSelect}
        />
        {/* Top Rated lives here, under the graph: it reflects the active
            search + year/decade/source filters (top of `filtered`). */}
        <ShowCarousel title="Top Rated" items={topRatedItems} />
        <SearchFilter
          searchQuery={searchQuery}
          onSearchChange={handleSearchChange}
          sortBy={sortBy}
          onSortChange={handleSortChange}
          selectedDecade={selectedDecade}
          onDecadeChange={handleDecadeChange}
          selectedSource={selectedSource}
          onSourceChange={handleSourceChange}
          includeNoRecordings={includeNoRecordings}
          onIncludeNoRecordingsChange={(v) => {
            setIncludeNoRecordings(v);
            setCurrentPage(0);
          }}
        />
        <ShowList
          shows={filtered}
          currentPage={currentPage}
          onPageChange={setCurrentPage}
        />
      </section>
    </>
  );
}
