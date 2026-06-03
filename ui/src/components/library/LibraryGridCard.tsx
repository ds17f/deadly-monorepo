"use client";

import { useState, type ReactNode } from "react";
import Link from "next/link";
import type { LibraryItem } from "./libraryItem";

// Artwork-forward grid card, mirroring the native Favorites grid: a square
// cover, then markers + date / venue / location. The optional `menu` (a
// RowActionsMenu) overlays the cover's top-right corner. The whole card links
// to the show; the menu sits above the link so its clicks don't navigate.
export default function LibraryGridCard({
  item,
  menu,
}: {
  item: LibraryItem;
  menu?: ReactNode;
}) {
  const [errored, setErrored] = useState(false);
  const src = errored || !item.image ? "/cover-fallback.png" : item.image;

  return (
    <div className="group relative">
      <Link href={`/shows/${item.showId}`} className="block">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={src}
          alt={item.dateLabel}
          loading="lazy"
          referrerPolicy="no-referrer"
          onError={() => setErrored(true)}
          className="aspect-square w-full rounded-lg bg-white/5 object-cover transition group-hover:opacity-90"
        />
        <div className="mt-2.5 px-0.5">
          <p className="flex items-center gap-1.5 truncate text-base font-semibold text-white">
            {item.pinned && (
              <svg
                width="15"
                height="15"
                viewBox="0 0 24 24"
                fill="currentColor"
                className="flex-shrink-0 text-deadly-accent"
                aria-label="Pinned"
              >
                <path d="M16 3v2l-1 1v5l3 3v2h-5v6h-2v-6H4v-2l3-3V6L6 5V3z" />
              </svg>
            )}
            {item.rating != null && (
              <span className="flex-shrink-0 text-yellow-400" aria-label="Rated">
                ★
              </span>
            )}
            <span className="truncate">{item.dateLabel}</span>
          </p>
          {item.venue && (
            <p className="mt-0.5 truncate text-sm text-white/60">{item.venue}</p>
          )}
          {item.location && (
            <p className="truncate text-sm text-white/40">{item.location}</p>
          )}
        </div>
      </Link>

      {menu && (
        <div className="absolute right-1.5 top-1.5 opacity-0 transition group-hover:opacity-100 focus-within:opacity-100">
          {menu}
        </div>
      )}
    </div>
  );
}
