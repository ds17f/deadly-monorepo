// Normalized model shared by the three /me library tabs (Favorites / Recent /
// Reviews). Each tab fetches its own enriched endpoint, maps the rows into
// LibraryItem via the adapters here, and hands them to <LibraryView>. Sort
// comparators and the search predicate live here so the tabs stay declarative.

import type { FavoriteShow, RecentShow, ShowReview } from "@/types/userdata";
import { formatShowDate, formatLocation } from "@/components/show/showFormat";
import { parseYear } from "./DecadeCascadeFilter";

export type LibraryKind = "favorite" | "recent" | "review";

export interface LibraryItem {
  showId: string;
  dateLabel: string;
  isoDate: string; // YYYY-MM-DD for show-date sorting
  year: number | null;
  venue: string | null;
  location: string | null; // "City, ST"
  image?: string | null;
  bestRecordingId?: string | null;
  // markers
  pinned: boolean;
  rating: number | null;
  // per-kind sort keys (0 when N/A)
  addedAt: number;
  lastPlayedAt: number;
  playCount: number;
  updatedAt: number;
}

function isoOf(show: { showId: string; date?: string | null }): string {
  return (show.date ?? show.showId).slice(0, 10);
}

export function favoriteToItem(f: FavoriteShow): LibraryItem {
  return {
    showId: f.showId,
    dateLabel: formatShowDate(f),
    isoDate: isoOf(f),
    year: parseYear(f),
    venue: f.venue ?? null,
    location: formatLocation(f),
    image: f.image,
    bestRecordingId: f.bestRecordingId,
    pinned: f.isPinned,
    rating: typeof f.customRating === "number" ? f.customRating : null,
    addedAt: f.addedAt,
    lastPlayedAt: 0,
    playCount: 0,
    updatedAt: 0,
  };
}

export function recentToItem(r: RecentShow): LibraryItem {
  return {
    showId: r.showId,
    dateLabel: formatShowDate(r),
    isoDate: isoOf(r),
    year: parseYear(r),
    venue: r.venue ?? null,
    location: formatLocation(r),
    image: r.image,
    bestRecordingId: r.bestRecordingId,
    pinned: false,
    rating: typeof r.rating === "number" ? r.rating : null,
    addedAt: 0,
    lastPlayedAt: r.lastPlayedAt,
    playCount: r.totalPlayCount,
    updatedAt: 0,
  };
}

export function reviewToItem(rv: ShowReview): LibraryItem {
  return {
    showId: rv.showId,
    dateLabel: formatShowDate(rv),
    isoDate: isoOf(rv),
    year: parseYear(rv),
    venue: rv.venue ?? null,
    location: formatLocation(rv),
    image: rv.image,
    bestRecordingId: rv.bestRecordingId,
    pinned: false,
    rating: typeof rv.overallRating === "number" ? rv.overallRating : null,
    addedAt: 0,
    lastPlayedAt: 0,
    playCount: 0,
    updatedAt: rv.updatedAt ?? 0,
  };
}

// ---- Sorting ----

export type SortDir = "asc" | "desc";
export interface SortSpec {
  id: string;
  label: string;
}

export const SORTS_BY_KIND: Record<LibraryKind, SortSpec[]> = {
  favorite: [
    { id: "dateAdded", label: "Date Added" },
    { id: "showDate", label: "Show Date" },
    { id: "venue", label: "Venue" },
  ],
  recent: [
    { id: "lastPlayed", label: "Last Played" },
    { id: "showDate", label: "Show Date" },
    { id: "playCount", label: "Play Count" },
  ],
  review: [
    { id: "updated", label: "Recently Edited" },
    { id: "showDate", label: "Show Date" },
    { id: "rating", label: "Rating" },
  ],
};

function sortKey(it: LibraryItem, sortId: string): number | string {
  switch (sortId) {
    case "dateAdded":
      return it.addedAt;
    case "lastPlayed":
      return it.lastPlayedAt;
    case "playCount":
      return it.playCount;
    case "updated":
      return it.updatedAt;
    case "rating":
      return it.rating ?? -1;
    case "venue":
      return (it.venue ?? "").toLowerCase();
    case "showDate":
      return it.isoDate;
    default:
      return 0;
  }
}

// Sort by the chosen key/direction; pinned items always float to the top
// (Array.sort is stable, so the inner order is preserved).
export function sortItems(
  items: LibraryItem[],
  sortId: string,
  dir: SortDir,
): LibraryItem[] {
  const byKey = [...items].sort((a, b) => {
    const ka = sortKey(a, sortId);
    const kb = sortKey(b, sortId);
    const cmp = ka < kb ? -1 : ka > kb ? 1 : 0;
    return dir === "desc" ? -cmp : cmp;
  });
  return byKey.sort((a, b) => Number(b.pinned) - Number(a.pinned));
}

// ---- Search ----

export function matchesQuery(it: LibraryItem, query: string): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return `${it.dateLabel} ${it.venue ?? ""} ${it.location ?? ""}`
    .toLowerCase()
    .includes(q);
}
