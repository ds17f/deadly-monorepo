"use client";

import { useMemo, useState } from "react";
import type { ShowIndexEntry, CollectionSummary, YearBucket } from "@/types/homepage";
import HeroSection from "./HeroSection";
import TopRatedShows from "./TopRatedShows";
import CollectionsGrid from "./CollectionsGrid";
import YearTimeline from "./YearTimeline";
import SearchFilter, { type SortBy } from "./SearchFilter";
import ShowList from "./ShowList";

export default function HomeContent({
  showIndex,
  topRated,
  collections,
  yearData,
  totalShows,
}: {
  showIndex: ShowIndexEntry[];
  topRated: ShowIndexEntry[];
  collections: CollectionSummary[];
  yearData: YearBucket[];
  totalShows: number;
}) {
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedYear, setSelectedYear] = useState<number | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [sortBy, setSortBy] = useState<SortBy>("date");

  const filtered = useMemo(() => {
    let list = showIndex;

    if (selectedYear !== null) {
      list = list.filter(
        (s) => parseInt(s.d.slice(0, 4), 10) === selectedYear
      );
    }

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(
        (s) =>
          s.v.toLowerCase().includes(q) ||
          s.c.toLowerCase().includes(q) ||
          s.s.toLowerCase().includes(q) ||
          s.d.includes(q)
      );
    }

    if (sortBy === "rating") {
      list = [...list].sort((a, b) => b.r - a.r || b.ar - a.ar);
    }
    // date sort is the default order (already sorted by date in showIndex)

    return list;
  }, [showIndex, searchQuery, selectedYear, sortBy]);

  function handleSearchChange(q: string) {
    setSearchQuery(q);
    setCurrentPage(0);
  }

  function handleYearSelect(year: number | null) {
    setSelectedYear(year);
    setCurrentPage(0);
  }

  function handleSortChange(s: SortBy) {
    setSortBy(s);
    setCurrentPage(0);
  }

  return (
    <>
      <HeroSection totalShows={totalShows} />
      <TopRatedShows shows={topRated} />
      <CollectionsGrid collections={collections} />
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
      />
      <ShowList
        shows={filtered}
        currentPage={currentPage}
        onPageChange={setCurrentPage}
      />
    </>
  );
}
