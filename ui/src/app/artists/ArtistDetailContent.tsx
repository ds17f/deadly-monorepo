"use client";

import { useEffect, useMemo, useState } from "react";
import type { Artist } from "@/types/artist";
import type { ShowIndexEntry, CollectionSummary, YearBucket } from "@/types/homepage";
import {
  fetchArtist,
  fetchArtistShows,
  fetchArtistCollections,
  showRowToIndexEntry,
  buildYearHistogramFromShows,
  computeDecades,
  collectionRowToSummary,
} from "@/lib/artistApi";
import YearTimeline from "@/components/home/YearTimeline";
import SearchFilter, { type SortBy } from "@/components/home/SearchFilter";
import ShowList from "@/components/home/ShowList";
import TopRatedShows from "@/components/home/TopRatedShows";
import CollectionsGrid from "@/components/home/CollectionsGrid";
import GetTheApp from "@/components/home/GetTheApp";

function formatYears(artist: Artist): string {
  const from = artist.active_from;
  const to = artist.active_to;
  if (!from && !to) return "";
  if (from && !to) return artist.is_active ? `${from}\u2013present` : `${from}`;
  if (from && to) return `${from}\u2013${to}`;
  return "";
}

interface ShowRow {
  id: string; slug: string; artist_id: string; date: string; year: number;
  venue_name: string | null; city: string | null; state: string | null;
  country: string; recording_count: number; best_recording_id: string | null;
  best_source_type: string | null; avg_rating: number | null;
  total_reviews: number; cover_image_url: string | null;
  setlist_raw: string | null; song_list: string | null;
  lineup_raw: string | null; notes: string | null;
}

export default function ArtistDetailContent({ artistId: id }: { artistId: string }) {
  const [artist, setArtist] = useState<Artist | null>(null);
  const [allShows, setAllShows] = useState<ShowIndexEntry[]>([]);
  const [yearData, setYearData] = useState<YearBucket[]>([]);
  const [collections, setCollections] = useState<CollectionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filter state
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

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    setError(null);

    async function load() {
      try {
        const [artistData, collectionsData] = await Promise.all([
          fetchArtist(id),
          fetchArtistCollections(id),
        ]);
        setArtist(artistData);
        setCollections(collectionsData.map(collectionRowToSummary));

        // Fetch all shows (paginate through cursors)
        const allRows: ShowIndexEntry[] = [];
        const rawShows: ShowRow[] = [];
        let cursor: string | null = null;
        do {
          const result = await fetchArtistShows(id, {
            sort: "date_asc",
            limit: 200,
            cursor: cursor ?? undefined,
          });
          for (const row of result.shows) {
            allRows.push(showRowToIndexEntry(row));
            rawShows.push(row as ShowRow);
          }
          cursor = result.nextCursor;
        } while (cursor);

        setAllShows(allRows);
        setYearData(buildYearHistogramFromShows(rawShows));
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to load artist");
      } finally {
        setLoading(false);
      }
    }

    load();
  }, [id]);

  const decades = useMemo(() => {
    if (!artist) return undefined;
    return computeDecades(artist.active_from, artist.active_to);
  }, [artist]);

  const filtered = useMemo(() => {
    let list = allShows;

    if (!includeNoRecordings) {
      list = list.filter((s) => s.rc > 0);
    }

    if (selectedYear !== null) {
      list = list.filter(
        (s) => parseInt(s.d.slice(0, 4), 10) === selectedYear
      );
    }

    if (selectedDecade !== null) {
      list = list.filter((s) => {
        const y = parseInt(s.d.slice(0, 4), 10);
        return y >= selectedDecade.from && y <= selectedDecade.to;
      });
    }

    if (selectedSource !== null) {
      list = list.filter((s) => s.st.includes(selectedSource));
    }

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
  }, [allShows, searchQuery, selectedYear, selectedDecade, selectedSource, sortBy, includeNoRecordings]);

  const hasActiveFilter =
    selectedYear !== null ||
    selectedDecade !== null ||
    selectedSource !== null ||
    searchQuery.trim() !== "";

  const topRated = useMemo(() => {
    const source = hasActiveFilter ? filtered : allShows;
    return [...source].filter((s) => s.rc > 0).sort((a, b) => b.r - a.r).slice(0, 20);
  }, [hasActiveFilter, allShows, filtered]);

  const topRatedFilterLabel = useMemo(() => {
    const parts: string[] = [];
    if (selectedYear !== null) parts.push(String(selectedYear));
    else if (selectedDecade !== null) {
      parts.push(`${selectedDecade.from}s`);
    }
    if (selectedSource !== null) parts.push(selectedSource);
    if (searchQuery.trim()) parts.push(`"${searchQuery.trim()}"`);
    if (parts.length === 0) return undefined;
    return `Filtered: ${parts.join(" / ")}`;
  }, [selectedYear, selectedDecade, selectedSource, searchQuery]);

  function resetPage() {
    setCurrentPage(0);
  }

  function handleSearchChange(q: string) {
    setSearchQuery(q);
    resetPage();
  }

  function handleYearSelect(year: number | null) {
    setSelectedYear(year);
    if (year !== null) setSelectedDecade(null);
    resetPage();
  }

  function handleSortChange(s: SortBy) {
    setSortBy(s);
    resetPage();
  }

  function handleDecadeChange(d: { from: number; to: number } | null) {
    setSelectedDecade(d);
    if (d !== null) setSelectedYear(null);
    resetPage();
  }

  function handleSourceChange(s: string | null) {
    setSelectedSource(s);
    resetPage();
  }

  if (loading) {
    return <p className="text-sm text-white/50">Loading artist...</p>;
  }

  if (error || !artist) {
    return <p className="text-sm text-red-400">{error ?? "Artist not found"}</p>;
  }

  return (
    <div className="grid grid-cols-1 gap-x-12 lg:grid-cols-3">
      <div className="lg:col-span-2">
        <header className="mb-8">
          <p className="text-lg font-bold uppercase tracking-wider text-deadly-title">
            {artist.name}
          </p>
          {artist.short_name && (
            <span className="text-sm text-white/40">{artist.short_name}</span>
          )}
          <div className="mt-1 text-sm text-white/50">
            {formatYears(artist)}
          </div>
          {artist.description && (
            <p className="mt-2 max-w-2xl text-sm leading-relaxed text-white/60">
              {artist.description}
            </p>
          )}
          <div className="mt-3 flex items-center gap-4 text-sm text-white/50">
            <span>
              {artist.show_count.toLocaleString()} show
              {artist.show_count !== 1 ? "s" : ""}
            </span>
            {artist.recording_count > 0 && (
              <span>
                {artist.recording_count.toLocaleString()} recording
                {artist.recording_count !== 1 ? "s" : ""}
              </span>
            )}
          </div>
        </header>
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
            decades={decades}
          />
          <ShowList
            shows={filtered}
            currentPage={currentPage}
            onPageChange={setCurrentPage}
          />
        </div>
        <div className="mt-6 lg:mt-0">
          {artist.image_url && (
            <div className="mb-6 overflow-hidden rounded-lg bg-deadly-surface p-3">
              <img
                src={artist.image_url}
                alt={artist.name}
                className="w-full rounded"
              />
            </div>
          )}
          <GetTheApp />
          <TopRatedShows shows={topRated} filterLabel={topRatedFilterLabel} />
          {collections.length > 0 && (
            <CollectionsGrid collections={collections} />
          )}
        </div>
    </div>
  );
}
