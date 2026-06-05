"use client";

/**
 * The persistent left "Your Library" rail of the app shell — a compact *quick
 * look* at the user's library; the full experience lives at /me/*.
 *
 * Wired to live data via the same enriched endpoints the /me tabs use
 * (fetchFavoriteShows / fetchRecentShows / fetchReviews). Renders them as
 * labelled sections (Recently Played / My Reviews / Favorites) that link to
 * their /me page, with the shared decade→year cascade filter on top that
 * NARROWS every section at once. Signed-out users get gated prompts.
 *
 * Static-export safe: client component, hydrates from the API after load.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/contexts/AuthContext";
import {
  fetchFavoriteShows,
  fetchRecentShows,
  fetchReviews,
} from "@/lib/userDataApi";
import { useUserDataRefresh } from "@/lib/userDataEvents";
import type { FavoriteShow, RecentShow, ShowReview } from "@/types/userdata";
import { formatShowDate, formatLocation } from "@/components/show/showFormat";
import DecadeCascadeFilter, {
  type FilterNode,
  selectedYears,
  parseYear,
} from "@/components/library/DecadeCascadeFilter";

type Kind = "favorite" | "recent" | "review";

type Item = {
  showId: string;
  date: string;
  location: string | null;
  kind: Kind;
  pinned: boolean;
  rating: number | null;
  sortKey: number;
  year: number | null;
  image?: string | null;
};

// Sections, in display order. Each gets a header linking to its /me page;
// empty ones are hidden.
const SECTIONS: { kind: Kind; header: string; href: string }[] = [
  { kind: "recent", header: "Recently Played", href: "/me/recent" },
  { kind: "review", header: "My Reviews", href: "/me/reviews" },
  { kind: "favorite", header: "Favorites", href: "/me/favorites" },
];

function buildItems(
  favorites: FavoriteShow[],
  recent: RecentShow[],
  reviews: ShowReview[],
): Item[] {
  const items: Item[] = [];

  for (const f of favorites) {
    items.push({
      showId: f.showId,
      date: formatShowDate(f),
      location: formatLocation(f),
      kind: "favorite",
      pinned: f.isPinned,
      rating: null,
      sortKey: f.isPinned ? Number.MAX_SAFE_INTEGER : f.addedAt,
      year: parseYear(f),
      image: f.image,
    });
  }
  for (const r of recent) {
    items.push({
      showId: r.showId,
      date: formatShowDate(r),
      location: formatLocation(r),
      kind: "recent",
      pinned: false,
      rating: null,
      sortKey: r.lastPlayedAt,
      year: parseYear(r),
      image: r.image,
    });
  }
  for (const rv of reviews) {
    items.push({
      showId: rv.showId,
      date: formatShowDate(rv),
      location: formatLocation(rv),
      kind: "review",
      pinned: false,
      rating: typeof rv.overallRating === "number" ? rv.overallRating : null,
      sortKey: rv.updatedAt ?? 0,
      year: parseYear(rv),
      image: rv.image,
    });
  }
  return items.sort((a, b) => b.sortKey - a.sortKey);
}

// The secondary line under the date — section-appropriate, so a "Recent"
// section doesn't repeat "Recently played" on every row.
function metaLine(it: Item): string {
  const parts: string[] = [];
  if (it.kind === "favorite" && it.pinned) parts.push("Pinned");
  if (it.kind === "review")
    parts.push(it.rating != null ? `★ ${it.rating}` : "Review");
  if (it.location) parts.push(it.location);
  return parts.join(" · ");
}

function Tile({ image, label }: { image?: string | null; label: string }) {
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={image || "/cover-fallback.png"}
      alt={image ? "" : label}
      className="h-11 w-11 flex-shrink-0 rounded-md object-cover"
      referrerPolicy="no-referrer"
      loading="lazy"
    />
  );
}

function Row({ it }: { it: Item }) {
  const meta = metaLine(it);
  return (
    <Link
      href={`/shows/${it.showId}`}
      className="flex items-center gap-3 rounded-md px-2 py-2 transition hover:bg-white/10"
    >
      <Tile image={it.image} label={it.date} />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-semibold text-white">
          {it.date}
        </span>
        {meta && (
          <span className="block truncate text-xs text-white/50">{meta}</span>
        )}
      </span>
      {it.pinned && <span className="text-deadly-accent">📌</span>}
    </Link>
  );
}

function GatedPrompts() {
  return (
    <div className="flex flex-col gap-4 p-4 pt-5">
      <div className="rounded-lg bg-white/5 p-4">
        <p className="text-sm font-bold text-white">Save your favorite shows</p>
        <p className="mt-1 text-sm text-white/60">
          Log in to pin shows, build a favorites list, and pick up where you
          left off.
        </p>
        <Link
          href="/signin?callbackUrl=/"
          className="mt-3 inline-block rounded-full bg-white px-4 py-1.5 text-sm font-bold text-black hover:scale-[1.03]"
        >
          Log in
        </Link>
      </div>
      <div className="rounded-lg bg-white/5 p-4">
        <p className="text-sm font-bold text-white">Write reviews &amp; rate</p>
        <p className="mt-1 text-sm text-white/60">
          Share what you think of a performance and tag the best versions.
        </p>
      </div>
      <p className="mt-auto px-1 text-[11px] leading-relaxed text-white/40">
        Everything you save syncs to the mobile app —{" "}
        <span className="text-white/60">iOS 2.32+ or Android 2.31+</span>.
      </p>
    </div>
  );
}

export default function LibraryRail() {
  const { user, isLoading: authLoading } = useAuth();
  // The drill-down path through the cascade; empty = "All".
  const [path, setPath] = useState<FilterNode[]>([]);
  const [items, setItems] = useState<Item[] | null>(null);
  const [error, setError] = useState(false);

  const load = useCallback(() => {
    if (!user?.id) {
      setItems(null);
      return;
    }
    setError(false);
    Promise.all([
      fetchFavoriteShows().catch(() => [] as FavoriteShow[]),
      fetchRecentShows().catch(() => [] as RecentShow[]),
      fetchReviews().catch(() => [] as ShowReview[]),
    ])
      .then(([f, r, rv]) => setItems(buildItems(f, r, rv)))
      .catch(() => setError(true));
  }, [user?.id]);

  useEffect(() => {
    load();
  }, [load]);

  useUserDataRefresh(load);

  const years = useMemo(() => selectedYears(path), [path]);

  // Group the filtered items into ordered, non-empty sections.
  const sections = useMemo(() => {
    if (!items) return [];
    const match = (i: Item) =>
      years == null || (i.year != null && years.has(i.year));
    return SECTIONS.map((s) => ({
      ...s,
      rows: items.filter((i) => i.kind === s.kind && match(i)),
    })).filter((s) => s.rows.length > 0);
  }, [items, years]);

  const hasAny = (items?.length ?? 0) > 0;

  return (
    <aside className="hidden w-[280px] flex-shrink-0 flex-col rounded-lg bg-deadly-surface lg:flex">
      <Link
        href="/me"
        className="flex items-center gap-2 px-4 pt-4 text-sm font-bold text-white/70 transition hover:text-white"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
          <path d="M3 5h18v2H3zM3 11h18v2H3zM3 17h12v2H3z" />
        </svg>
        Your Library
      </Link>

      {authLoading ? (
        <div className="p-4 text-sm text-white/40">Loading…</div>
      ) : !user ? (
        <GatedPrompts />
      ) : (
        <>
          <DecadeCascadeFilter path={path} onChange={setPath} className="px-4 pt-3" />

          <div className="mt-2 flex-1 overflow-y-auto px-2 pb-2">
            {items === null ? (
              <div className="space-y-1 p-2">
                {Array.from({ length: 6 }).map((_, i) => (
                  <div key={i} className="h-14 animate-pulse rounded-md bg-white/5" />
                ))}
              </div>
            ) : error ? (
              <p className="p-3 text-sm text-white/40">Couldn&apos;t load your library.</p>
            ) : sections.length === 0 ? (
              <p className="p-3 text-sm text-white/40">
                {hasAny
                  ? "Nothing matches this filter."
                  : "Nothing here yet. Favorite a show or play something to fill this in."}
              </p>
            ) : (
              sections.map((s) => (
                <section key={s.kind} className="mb-3 last:mb-0">
                  <Link
                    href={s.href}
                    className="flex items-center justify-between px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-white/40 transition hover:text-white/70"
                  >
                    {s.header}
                    <span aria-hidden>›</span>
                  </Link>
                  {s.rows.map((it) => (
                    <Row key={it.kind + it.showId} it={it} />
                  ))}
                </section>
              ))
            )}
          </div>
        </>
      )}
    </aside>
  );
}
