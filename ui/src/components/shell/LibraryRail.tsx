"use client";

/**
 * The persistent left "Your Library" rail of the app shell.
 *
 * Wired to live data via the same enriched endpoints the /me tabs use
 * (fetchFavoriteShows / fetchRecentShows / fetchReviews). Renders them as
 * labelled sections (Recent / Reviews / Library), with a hierarchical
 * decade→season filter on top that NARROWS every section at once — mirroring
 * the native Favorites screen's filter chips (iosApp FavoritesScreen.swift).
 * Signed-out users get gated prompts.
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
  pinned: boolean;
  rating: number | null;
  sortKey: number;
  year: number | null;
  month: number | null;
  image?: string | null;
};

// Sections, in display order. Each gets a header; empty ones are hidden.
const SECTIONS: { kind: Kind; header: string }[] = [
  { kind: "recent", header: "Recently Played" },
  { kind: "review", header: "My Reviews" },
  { kind: "favorite", header: "Favorites" },
];

// Hierarchical decade→half→year filter, mirroring the native Search screen's
// cascade (HierarchicalFilterChips.swift). Tap a node to drill in, tap the
// selected node to step back; a leaf year collapses to a breadcrumb chip.
type FilterNode = { id: string; label: string; year?: number; children?: FilterNode[] };

const years = (a: number, b: number): FilterNode[] =>
  Array.from({ length: b - a + 1 }, (_, i) => ({
    id: String(a + i),
    label: String(a + i),
    year: a + i,
  }));

const FILTER_TREE: FilterNode[] = [
  { id: "60s", label: "60s", children: years(1965, 1969) },
  {
    id: "70s",
    label: "70s",
    children: [
      { id: "early_70s", label: "Early 70s", children: years(1970, 1974) },
      { id: "late_70s", label: "Late 70s", children: years(1975, 1979) },
    ],
  },
  {
    id: "80s",
    label: "80s",
    children: [
      { id: "early_80s", label: "Early 80s", children: years(1980, 1984) },
      { id: "late_80s", label: "Late 80s", children: years(1985, 1989) },
    ],
  },
  { id: "90s", label: "90s", children: years(1990, 1995) },
];

// Every leaf year reachable under a node — the set a selection filters to.
function leafYears(node: FilterNode): number[] {
  if (node.year != null) return [node.year];
  return (node.children ?? []).flatMap(leafYears);
}

// Shows are date-prefixed (YYYY-MM-DD-…); the enriched `date` is preferred.
function parseYearMonth(show: { showId: string; date?: string | null }): {
  year: number | null;
  month: number | null;
} {
  const iso = (show.date ?? show.showId).slice(0, 10);
  const m = /^(\d{4})-(\d{2})-\d{2}$/.exec(iso);
  if (!m) return { year: null, month: null };
  return { year: Number(m[1]), month: Number(m[2]) };
}

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
      ...parseYearMonth(f),
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
      ...parseYearMonth(r),
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
      ...parseYearMonth(rv),
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

function Chip({
  label,
  active = false,
  onClick,
}: {
  label: string;
  active?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`whitespace-nowrap rounded-full px-3 py-1 text-xs font-medium transition ${
        active
          ? "bg-deadly-accent text-white"
          : "bg-white/10 text-white/80 hover:bg-white/20"
      }`}
    >
      {label}
    </button>
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
  // The drill-down path through FILTER_TREE; empty = "All".
  const [path, setPath] = useState<FilterNode[]>([]);
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

  // The years the current selection narrows to (null = no filter).
  const selectedYears = useMemo<Set<number> | null>(
    () => (path.length === 0 ? null : new Set(leafYears(path[path.length - 1]))),
    [path],
  );

  // Group the filtered items into ordered, non-empty sections.
  const sections = useMemo(() => {
    if (!items) return [];
    const match = (i: Item) =>
      selectedYears == null || (i.year != null && selectedYears.has(i.year));
    return SECTIONS.map((s) => ({
      ...s,
      rows: items.filter((i) => i.kind === s.kind && match(i)),
    })).filter((s) => s.rows.length > 0);
  }, [items, selectedYears]);

  const hasAny = (items?.length ?? 0) > 0;
  const deepest = path[path.length - 1];

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
          {/* Hierarchical decade→half→year cascade, mirroring the native
              Search filter — but wrapped across rows (not scrolled) so the
              year chips fit the rail. Narrows every section at once. */}
          <div className="flex flex-wrap gap-1.5 px-4 pt-3">
            <Chip label="All" active={path.length === 0} onClick={() => setPath([])} />

            {path.length === 0 ? (
              // Root: the decades.
              FILTER_TREE.map((n) => (
                <Chip key={n.id} label={n.label} onClick={() => setPath([n])} />
              ))
            ) : !deepest.children?.length ? (
              // Leaf year: collapse the whole path into one breadcrumb chip;
              // tapping it steps back up a level.
              <Chip
                active
                label={path.map((n) => n.label).join(" > ")}
                onClick={() => setPath(path.slice(0, -1))}
              />
            ) : (
              // Intermediate: the selected node (tap to step back) + children.
              <>
                <Chip
                  active
                  label={deepest.label}
                  onClick={() => setPath(path.slice(0, -1))}
                />
                {deepest.children.map((c) => (
                  <Chip
                    key={c.id}
                    label={c.label}
                    onClick={() => setPath([...path, c])}
                  />
                ))}
              </>
            )}
          </div>

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
                  <h3 className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-white/40">
                    {s.header}
                  </h3>
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
