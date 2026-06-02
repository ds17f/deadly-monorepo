"use client";

/**
 * A horizontal, cover-art carousel of shows — the mobile-app home unit, now in
 * the wide middle column where it has room. Used for TIGDH, Trending, Fan
 * Favorites, Recently Played, and Top Rated.
 */

import Link from "next/link";
import { useState } from "react";

export interface CarouselItem {
  showId: string;
  date: string; // already formatted for display
  venue?: string | null;
  location?: string | null;
  image?: string | null;
  trailing?: string | null; // e.g. "315 ♥" / play count
}

function Card({ item }: { item: CarouselItem }) {
  const [broken, setBroken] = useState(false);
  const src = item.image && !broken ? item.image : "/logo.png";
  const isLogo = src === "/logo.png";
  return (
    <Link
      href={`/shows/${item.showId}`}
      className="flex w-40 flex-shrink-0 snap-start flex-col gap-2 rounded-lg p-2 transition hover:bg-white/10"
    >
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={src}
        alt=""
        loading="lazy"
        referrerPolicy="no-referrer"
        onError={() => setBroken(true)}
        className={`aspect-square w-full rounded-md bg-white/5 shadow-md ${
          isLogo ? "object-contain p-5 opacity-70" : "object-cover"
        }`}
      />
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-white">{item.date}</p>
        {item.venue && (
          <p className="truncate text-xs text-white/50">{item.venue}</p>
        )}
        {item.location && (
          <p className="truncate text-xs text-white/40">{item.location}</p>
        )}
        {item.trailing && (
          <p className="truncate text-xs text-deadly-star">{item.trailing}</p>
        )}
      </div>
    </Link>
  );
}

export default function ShowCarousel({
  title,
  items,
}: {
  title: string;
  items: CarouselItem[];
}) {
  if (items.length === 0) return null;
  return (
    <section className="mb-6">
      <h3 className="mb-2 text-lg font-bold text-white">{title}</h3>
      <div className="-mx-1 flex snap-x gap-1 overflow-x-auto px-1 pb-2">
        {items.map((it) => (
          <Card key={it.showId} item={it} />
        ))}
      </div>
    </section>
  );
}
