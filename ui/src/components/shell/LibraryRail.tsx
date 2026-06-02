"use client";

/**
 * The persistent left "Your Library" rail of the app shell.
 *
 * Wired to live data via the same enriched endpoints the /me tabs use
 * (fetchFavoriteShows / fetchRecentShows / fetchReviews). Merges them into a
 * single list with filter pills. Signed-out users get gated prompts; this
 * mirrors /mockup and /mockup/logged-out.
 *
 * Static-export safe: client component, hydrates from the API after load.
 */

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/contexts/AuthContext";
import {
  fetchFavoriteShows,
  fetchRecentShows,
  fetchReviews,
} from "@/lib/userDataApi";
import type { FavoriteShow, RecentShow, ShowReview } from "@/types/userdata";
import { formatShowDate, formatLocation } from "@/components/show/showFormat";

type Kind = "favorite" | "recent" | "review";

type Item = {
  showId: string;
  date: string;
  location: string | null;
  kind: Kind;
  sub: string;
  pinned: boolean;
  sortKey: number;
  image?: string | null;
};

const FILTERS: { label: string; kind: Kind | null }[] = [
  { label: "All", kind: null },
  { label: "Favorites", kind: "favorite" },
  { label: "Recent", kind: "recent" },
  { label: "Reviews", kind: "review" },
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
      sub: f.isPinned ? "Pinned" : "Favorite",
      pinned: f.isPinned,
      sortKey: f.isPinned ? Number.MAX_SAFE_INTEGER : f.addedAt,
      image: f.image,
    });
  }
  for (const r of recent) {
    items.push({
      showId: r.showId,
      date: formatShowDate(r),
      location: formatLocation(r),
      kind: "recent",
      sub: "Recently played",
      pinned: false,
      sortKey: r.lastPlayedAt,
      image: r.image,
    });
  }
  for (const rv of reviews) {
    const rating =
      typeof rv.overallRating === "number" ? ` · ★ ${rv.overallRating}` : "";
    items.push({
      showId: rv.showId,
      date: formatShowDate(rv),
      location: formatLocation(rv),
      kind: "review",
      sub: `Your review${rating}`,
      pinned: false,
      sortKey: rv.updatedAt ?? 0,
      image: rv.image,
    });
  }
  return items.sort((a, b) => b.sortKey - a.sortKey);
}

function Tile({ image, label }: { image?: string | null; label: string }) {
  if (image) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img
        src={image}
        alt=""
        className="h-11 w-11 flex-shrink-0 rounded-md object-cover"
        referrerPolicy="no-referrer"
        loading="lazy"
      />
    );
  }
  return (
    <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-md bg-gradient-to-br from-deadly-accent/30 to-deadly-blue/20 text-xs text-white/70">
      ⚡<span className="sr-only">{label}</span>
    </span>
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
  const [active, setActive] = useState<Kind | null>(null);
  const [items, setItems] = useState<Item[] | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!user?.id) {
      setItems(null);
      return;
    }
    let cancelled = false;
    setError(false);
    Promise.all([
      fetchFavoriteShows().catch(() => [] as FavoriteShow[]),
      fetchRecentShows().catch(() => [] as RecentShow[]),
      fetchReviews().catch(() => [] as ShowReview[]),
    ])
      .then(([f, r, rv]) => {
        if (!cancelled) setItems(buildItems(f, r, rv));
      })
      .catch(() => {
        if (!cancelled) setError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [user?.id]);

  const shown = useMemo(
    () => (active ? (items ?? []).filter((i) => i.kind === active) : items ?? []),
    [active, items],
  );

  return (
    <aside className="hidden w-[280px] flex-shrink-0 flex-col rounded-lg bg-deadly-surface lg:flex">
      <div className="flex items-center gap-2 px-4 pt-4 text-sm font-bold text-white/70">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
          <path d="M3 5h18v2H3zM3 11h18v2H3zM3 17h12v2H3z" />
        </svg>
        Your Library
      </div>

      {authLoading ? (
        <div className="p-4 text-sm text-white/40">Loading…</div>
      ) : !user ? (
        <GatedPrompts />
      ) : (
        <>
          <div className="flex flex-wrap gap-2 px-4 pt-3">
            {FILTERS.map((f) => (
              <button
                key={f.label}
                onClick={() => setActive(f.kind)}
                className={`rounded-full px-3 py-1 text-xs font-medium transition ${
                  active === f.kind
                    ? "bg-white text-black"
                    : "bg-white/10 text-white/80 hover:bg-white/20"
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>

          <div className="mt-3 flex-1 overflow-y-auto px-2 pb-2">
            {items === null ? (
              <div className="space-y-1 p-2">
                {Array.from({ length: 6 }).map((_, i) => (
                  <div key={i} className="h-14 animate-pulse rounded-md bg-white/5" />
                ))}
              </div>
            ) : error ? (
              <p className="p-3 text-sm text-white/40">Couldn&apos;t load your library.</p>
            ) : shown.length === 0 ? (
              <p className="p-3 text-sm text-white/40">
                Nothing here yet. Favorite a show or play something to fill this
                in.
              </p>
            ) : (
              shown.map((it) => (
                <Link
                  key={it.kind + it.showId}
                  href={`/shows/${it.showId}`}
                  className="flex items-center gap-3 rounded-md px-2 py-2 transition hover:bg-white/10"
                >
                  <Tile image={it.image} label={it.date} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold text-white">
                      {it.date}
                    </span>
                    <span className="block truncate text-xs text-white/50">
                      {it.sub}
                      {it.location ? ` · ${it.location}` : ""}
                    </span>
                  </span>
                  {it.pinned && <span className="text-deadly-accent">📌</span>}
                </Link>
              ))
            )}
          </div>
        </>
      )}

      <div className="border-t border-white/10 px-4 py-3 text-[11px] text-white/30">
        <Link href="/privacy" className="hover:text-white/60">
          Privacy
        </Link>
        <span className="px-1.5">·</span>
        <a
          href="https://github.com/ds17f/deadly-monorepo"
          target="_blank"
          rel="noopener noreferrer"
          className="hover:text-white/60"
        >
          GitHub
        </a>
      </div>
    </aside>
  );
}
