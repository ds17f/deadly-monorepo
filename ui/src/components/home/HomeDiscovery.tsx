"use client";

/**
 * The discovery section of the home middle column — the mobile-app home, in
 * carousels: Recently Played (signed in) · Today in Grateful Dead History ·
 * Trending · Fan Favorites · Collections · Top Rated.
 *
 * Dynamic units (Recently Played / Trending / Fan Favorites) hydrate from the
 * API; TIGDH and Top Rated come from the build-time show index already in
 * memory. Bare show_ids from the analytics endpoints resolve against that index.
 */

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import type { ShowIndexEntry, CollectionSummary } from "@/types/homepage";
import type { RecentShow } from "@/types/userdata";
import { useAuth } from "@/contexts/AuthContext";
import { fetchRecentShows } from "@/lib/userDataApi";
import { formatShowDate, formatLocation } from "@/components/show/showFormat";
import { fetchTrending, fetchPopular, type PopularShow } from "@/lib/discoveryApi";
import ShowCarousel, { type CarouselItem } from "./ShowCarousel";

function fmtDate(d: string): string {
  const [y, m, day] = d.split("-").map(Number);
  if (!y) return d;
  return new Date(y, (m ?? 1) - 1, day ?? 1).toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

function toItem(s: ShowIndexEntry, trailing?: string): CarouselItem {
  return {
    showId: s.id,
    date: fmtDate(s.d),
    venue: s.v,
    location: [s.c, s.s].filter(Boolean).join(", "),
    image: s.img || null,
    trailing,
  };
}

const todayKey = () => {
  const n = new Date();
  return `${String(n.getMonth() + 1).padStart(2, "0")}-${String(n.getDate()).padStart(2, "0")}`;
};

export default function HomeDiscovery({
  showIndex,
  topRated,
  collections,
}: {
  showIndex: ShowIndexEntry[];
  topRated: ShowIndexEntry[];
  collections: CollectionSummary[];
}) {
  const { user } = useAuth();
  const byId = useMemo(() => {
    const m = new Map<string, ShowIndexEntry>();
    for (const e of showIndex) m.set(e.id, e);
    return m;
  }, [showIndex]);

  const tigdh = useMemo(() => {
    const key = todayKey();
    return showIndex
      .filter((e) => e.d.slice(5) === key)
      .sort((a, b) => b.r - a.r)
      .slice(0, 12)
      .map((s) => toItem(s));
  }, [showIndex]);

  const [recent, setRecent] = useState<CarouselItem[]>([]);
  const [trending, setTrending] = useState<CarouselItem[]>([]);
  const [popular, setPopular] = useState<CarouselItem[]>([]);

  useEffect(() => {
    let cancelled = false;

    if (user?.id) {
      fetchRecentShows()
        .then((rows: RecentShow[]) => {
          if (cancelled) return;
          setRecent(
            rows.slice(0, 12).map((r) => ({
              showId: r.showId,
              date: formatShowDate(r),
              location: formatLocation(r),
              image: r.image,
            })),
          );
        })
        .catch(() => {});
    } else {
      setRecent([]);
    }

    fetchTrending()
      .then((res) => {
        if (cancelled) return;
        const w = res.windows;
        const win = [w.now, w.week, w.month, w.all].find((a) => a.length > 0) ?? [];
        setTrending(
          win
            .map((t) => byId.get(t.show_id))
            .filter((s): s is ShowIndexEntry => s != null)
            .map((s) => toItem(s)),
        );
      })
      .catch(() => {});

    fetchPopular()
      .then((res) => {
        if (cancelled) return;
        const pools: PopularShow[][] = [
          res.decades["60s"],
          res.decades["70s"],
          res.decades["80s"],
          res.decades["90s"],
        ];
        const items: CarouselItem[] = [];
        for (const pool of pools) {
          for (const p of [...(pool ?? [])].sort((a, b) => b.favorites - a.favorites).slice(0, 3)) {
            const s = byId.get(p.show_id);
            if (s) items.push(toItem(s, `${p.favorites} ♥`));
          }
        }
        setPopular(items);
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, [byId, user?.id]);

  const topRatedItems = useMemo(
    () => topRated.slice(0, 15).map((s) => toItem(s, s.r > 0 ? `★ ${s.r.toFixed(1)}` : undefined)),
    [topRated],
  );

  return (
    <div className="mb-4">
      <ShowCarousel title="Recently Played" items={recent} />
      <ShowCarousel title="Today in Grateful Dead History" items={tigdh} />
      <ShowCarousel title="Trending" items={trending} />
      <ShowCarousel title="Fan Favorites" items={popular} />

      {collections.length > 0 && (
        <section className="mb-6">
          <h3 className="mb-2 text-lg font-bold text-white">Collections</h3>
          <div className="-mx-1 flex snap-x gap-2 overflow-x-auto px-1 pb-2">
            {collections.map((c) => (
              <Link
                key={c.id}
                href={`/?collection=${encodeURIComponent(c.id)}`}
                title={c.description}
                className="flex-shrink-0 snap-start rounded-full bg-deadly-surface px-4 py-2 text-sm text-white/80 transition hover:bg-white/10"
              >
                {c.name}
                <span className="ml-1.5 text-xs text-white/40">{c.total_shows}</span>
              </Link>
            ))}
          </div>
        </section>
      )}

      <ShowCarousel title="Top Rated" items={topRatedItems} />
    </div>
  );
}
