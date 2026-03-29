"use client";

import Link from "next/link";
import type { AdjacentShows } from "@/lib/artistApi";
import { artistUrl, showUrl } from "@/lib/urls";

function formatShortDate(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export default function DynamicShowNav({
  adjacent,
  artistId,
  artistName,
}: {
  adjacent: AdjacentShows;
  artistId: string;
  artistName: string;
}) {
  const { prev, next } = adjacent;

  return (
    <nav className="mb-6 flex flex-col gap-3">
      <Link
        href={artistUrl(artistId)}
        className="text-sm text-white/40 hover:text-white/70 transition-colors"
      >
        &larr; {artistName}
      </Link>

      {/* Mobile: compact < Play > */}
      <div className="flex w-full items-center justify-between lg:hidden">
        {prev ? (
          <Link
            href={showUrl(prev.artist_id, prev.id)}
            className="rounded-lg px-4 py-2 text-xl text-white/40 transition-colors hover:bg-white/5 hover:text-white/70"
          >
            &lsaquo;
          </Link>
        ) : (
          <div className="px-4 py-2 text-xl text-transparent">&lsaquo;</div>
        )}
        <a
          href="#listen"
          className="rounded-lg border border-white/20 px-5 py-2 text-sm font-semibold text-white transition-colors hover:bg-white/10"
        >
          Listen Now
        </a>
        {next ? (
          <Link
            href={showUrl(next.artist_id, next.id)}
            className="rounded-lg px-4 py-2 text-xl text-white/40 transition-colors hover:bg-white/5 hover:text-white/70"
          >
            &rsaquo;
          </Link>
        ) : (
          <div className="px-4 py-2 text-xl text-transparent">&rsaquo;</div>
        )}
      </div>

      {/* Desktop: full prev/next with details */}
      <div className="hidden w-full items-center justify-between lg:flex">
        {prev ? (
          <Link
            href={showUrl(prev.artist_id, prev.id)}
            className="group flex items-center gap-2 rounded-lg px-3 py-2 transition-colors hover:bg-white/5"
          >
            <span className="text-white/40 group-hover:text-white/70">&larr;</span>
            <div>
              <div className="text-sm font-medium text-white">
                {formatShortDate(prev.date)}
              </div>
              <div className="text-xs text-white/40 truncate max-w-48">
                {prev.venue_name ?? "Unknown Venue"}
              </div>
            </div>
          </Link>
        ) : (
          <div />
        )}
        {next ? (
          <Link
            href={showUrl(next.artist_id, next.id)}
            className="group flex items-center gap-2 rounded-lg px-3 py-2 text-right transition-colors hover:bg-white/5"
          >
            <div>
              <div className="text-sm font-medium text-white">
                {formatShortDate(next.date)}
              </div>
              <div className="text-xs text-white/40 truncate max-w-48">
                {next.venue_name ?? "Unknown Venue"}
              </div>
            </div>
            <span className="text-white/40 group-hover:text-white/70">&rarr;</span>
          </Link>
        ) : (
          <div />
        )}
      </div>
    </nav>
  );
}
