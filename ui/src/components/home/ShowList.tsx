"use client";

import Link from "next/link";
import type { ShowIndexEntry } from "@/types/homepage";
import { showUrl } from "@/lib/urls";

const PAGE_SIZE = 50;

function formatDate(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export default function ShowList({
  shows,
  artistId,
  currentPage,
  onPageChange,
}: {
  shows: ShowIndexEntry[];
  artistId: string;
  currentPage: number;
  onPageChange: (page: number) => void;
}) {
  const totalPages = Math.ceil(shows.length / PAGE_SIZE);
  const start = currentPage * PAGE_SIZE;
  const page = shows.slice(start, start + PAGE_SIZE);

  return (
    <section>
      <div className="mb-4 text-sm text-white/50">
        {shows.length.toLocaleString()} show{shows.length !== 1 ? "s" : ""}
      </div>

      <div className="space-y-1">
        {page.map((show) => (
          <Link
            key={show.id}
            href={showUrl(artistId, show.id)}
            className="flex items-center gap-4 rounded-lg px-3 py-2.5 transition-colors hover:bg-deadly-surface"
          >
            <span className="w-28 shrink-0 text-sm text-deadly-red">
              {formatDate(show.d)}
            </span>
            <span className="min-w-0 flex-1 truncate text-sm text-white">
              {show.v}
            </span>
            <span className="hidden shrink-0 text-sm text-white/50 sm:block">
              {show.c}, {show.s}
            </span>
            {show.r > 0 && (
              <span className="shrink-0 text-sm text-white/50">
                <span className="text-deadly-star">{"\u2605"}</span>{" "}
                {show.r.toFixed(1)}
              </span>
            )}
            {show.rc > 0 && (
              <span className="hidden shrink-0 text-xs text-white/30 md:block">
                {show.rc} rec
              </span>
            )}
          </Link>
        ))}
      </div>

      {totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-2">
          <button
            onClick={() => onPageChange(currentPage - 1)}
            disabled={currentPage === 0}
            className="rounded-lg bg-deadly-surface px-3 py-1.5 text-sm text-white/60 transition-colors hover:bg-white/10 disabled:opacity-30"
          >
            Prev
          </button>
          <span className="text-sm text-white/40">
            {currentPage + 1} / {totalPages}
          </span>
          <button
            onClick={() => onPageChange(currentPage + 1)}
            disabled={currentPage >= totalPages - 1}
            className="rounded-lg bg-deadly-surface px-3 py-1.5 text-sm text-white/60 transition-colors hover:bg-white/10 disabled:opacity-30"
          >
            Next
          </button>
        </div>
      )}
    </section>
  );
}
