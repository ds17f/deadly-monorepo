"use client";

import { useMemo, useState } from "react";
import type { ShowIndexEntry, CollectionSummary, YearBucket } from "@/types/homepage";
import HeroSection from "./HeroSection";
import GetTheApp from "./GetTheApp";
import TopRatedShows from "./TopRatedShows";
import CollectionsGrid from "./CollectionsGrid";
import YearTimeline from "./YearTimeline";
import SearchFilter, { type SortBy } from "./SearchFilter";
import ShowList from "./ShowList";

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

    // Text search
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(
        (s) =>
          s.v?.toLowerCase().includes(q) ||
          s.c?.toLowerCase().includes(q) ||
          s.s?.toLowerCase().includes(q) ||
          s.d.includes(q)
      );
    }

    if (sortBy === "rating") {
      list = [...list].sort((a, b) => b.r - a.r || b.ar - a.ar);
    }

    return list;
  }, [showIndex, searchQuery, selectedYear, selectedDecade, selectedSource, sortBy, includeNoRecordings]);

  const hasActiveFilter =
    selectedYear !== null ||
    selectedDecade !== null ||
    selectedSource !== null ||
    searchQuery.trim() !== "";

  const topRated = useMemo(() => {
    if (!hasActiveFilter) return topRatedAll;
    // Recompute top rated from filtered list
    return [...filtered].sort((a, b) => b.r - a.r).slice(0, 20);
  }, [hasActiveFilter, topRatedAll, filtered]);

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
    <div className="grid grid-cols-1 gap-x-12 lg:grid-cols-3">
      <div className="lg:col-span-2">
        <HeroSection totalShows={totalShows} />
        <YearTimeline
            yearData={yearData}
            selectedYear={selectedYear}
            onSelectYear={handleYearSelect}
          />
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
        </div>
      <div className="mt-6 lg:mt-0">
        <GetTheApp />
        <TopRatedShows shows={topRated} />
        <CollectionsGrid collections={collections} />
      </div>
    </div>
  );
}
