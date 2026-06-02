"use client";

/**
 * The home discovery rail — the dynamic, client-hydrated counterpart to the
 * static catalog in the middle pane. Ports the mobile home units
 * (HomeScreen.swift): Today in Grateful Dead History, Trending, Fan Favorites.
 *
 * Lives in the shell's narrow right pane on desktop, and stacks above the
 * content on mobile (RightRailSlot mobilePlacement="above"). Because the rail
 * is narrow, each unit is a VERTICAL LIST of library-style cards (ShowRow) —
 * not a carousel. (Carousels would suit a future main-window treatment.)
 *
 * The analytics endpoints return bare show_ids; we resolve them to display
 * metadata against the home-page showIndex (already hydrated here), so no
 * enriched endpoint is needed. TIGDH needs no API at all — it's a date match
 * over the same index.
 */

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import type { ShowIndexEntry } from "@/types/homepage";
import GetTheApp from "./GetTheApp";
import {
  fetchTrending,
  fetchPopular,
  type PopularShow,
} from "@/lib/discoveryApi";

function formatDate(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

function location(s: ShowIndexEntry): string {
  return [s.c, s.s].filter(Boolean).join(", ");
}

// MM-DD for today and ±3 days (handles month/year boundaries via Date math).
function anniversaryKeys(): { today: string; week: Set<string> } {
  const now = new Date();
  const key = (d: Date) =>
    `${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  const week = new Set<string>();
  for (let offset = -3; offset <= 3; offset++) {
    const d = new Date(now);
    d.setDate(now.getDate() + offset);
    week.add(key(d));
  }
  return { today: key(now), week };
}

// A clean, borderless entry matching the left library rail: small ticket
// tile · date (primary) · location (secondary), hover highlight only.
function Row({ show, trailing }: { show: ShowIndexEntry; trailing?: string }) {
  return (
    <Link
      href={`/shows/${show.id}`}
      className="flex items-center gap-3 rounded-md px-2 py-1.5 transition hover:bg-white/10"
    >
      {show.img ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={show.img}
          alt=""
          loading="lazy"
          referrerPolicy="no-referrer"
          className="h-11 w-11 flex-shrink-0 rounded-md object-cover"
        />
      ) : (
        <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-md bg-gradient-to-br from-deadly-accent/30 to-deadly-blue/20 text-xs text-white/70">
          ⚡
        </span>
      )}
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-semibold text-white">
          {formatDate(show.d)}
        </span>
        <span className="block truncate text-xs text-white/50">
          {location(show)}
        </span>
      </span>
      {trailing && (
        <span className="flex-shrink-0 text-xs text-white/40">{trailing}</span>
      )}
    </Link>
  );
}

function Unit({
  title,
  rows,
}: {
  title: string;
  rows: { show: ShowIndexEntry; trailing?: string }[];
}) {
  if (rows.length === 0) return null;
  return (
    <section className="mb-5">
      <h3 className="mb-1 px-2 text-sm font-bold text-white">{title}</h3>
      <div className="space-y-0.5">
        {rows.map(({ show, trailing }) => (
          <Row key={show.id} show={show} trailing={trailing} />
        ))}
      </div>
    </section>
  );
}

export default function DiscoveryRail({
  showIndex,
}: {
  showIndex: ShowIndexEntry[];
}) {
  const byId = useMemo(() => {
    const m = new Map<string, ShowIndexEntry>();
    for (const e of showIndex) m.set(e.id, e);
    return m;
  }, [showIndex]);

  // Today in Grateful Dead History — exact MM-DD match, else this-week window.
  const tigdh = useMemo(() => {
    const { today, week } = anniversaryKeys();
    const exact = showIndex
      .filter((e) => e.d.slice(5) === today)
      .sort((a, b) => b.r - a.r);
    if (exact.length > 0)
      return { title: "Today in Grateful Dead History", shows: exact.slice(0, 5) };
    const nearby = showIndex
      .filter((e) => week.has(e.d.slice(5)))
      .sort((a, b) => b.r - a.r);
    return {
      title: "This Week in Grateful Dead History",
      shows: nearby.slice(0, 5),
    };
  }, [showIndex]);

  const [trending, setTrending] = useState<ShowIndexEntry[]>([]);
  const [popular, setPopular] = useState<
    { show: ShowIndexEntry; favorites: number }[]
  >([]);

  useEffect(() => {
    let cancelled = false;

    fetchTrending()
      .then((res) => {
        if (cancelled) return;
        // Prefer the 24h window; fall back to wider windows so the unit is
        // never empty on low-traffic days (mirrors the TIGDH week fallback).
        const w = res.windows;
        const window =
          [w.now, w.week, w.month, w.all].find((arr) => arr.length > 0) ?? [];
        setTrending(
          window
            .map((t) => byId.get(t.show_id))
            .filter((s): s is ShowIndexEntry => s != null)
            .slice(0, 6),
        );
      })
      .catch(() => {});

    fetchPopular()
      .then((res) => {
        if (cancelled) return;
        // Preserve decade texture: top 2 per decade by favorites, in era order.
        const pools: PopularShow[][] = [
          res.decades["60s"],
          res.decades["70s"],
          res.decades["80s"],
          res.decades["90s"],
        ];
        const picked: { show: ShowIndexEntry; favorites: number }[] = [];
        for (const pool of pools) {
          for (const p of [...(pool ?? [])]
            .sort((a, b) => b.favorites - a.favorites)
            .slice(0, 2)) {
            const show = byId.get(p.show_id);
            if (show) picked.push({ show, favorites: p.favorites });
          }
        }
        setPopular(picked.slice(0, 6));
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, [byId]);

  return (
    <div className="rounded-lg bg-deadly-surface p-3 lg:min-h-full">
      <Unit
        title={tigdh.title}
        rows={tigdh.shows.map((show) => ({ show }))}
      />
      <Unit title="Trending" rows={trending.map((show) => ({ show }))} />
      <Unit
        title="Fan Favorites"
        rows={popular.map(({ show, favorites }) => ({
          show,
          trailing: `${favorites} ♥`,
        }))}
      />
      <GetTheApp />
    </div>
  );
}
