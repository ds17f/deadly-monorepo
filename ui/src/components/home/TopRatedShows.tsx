"use client";

import { useMemo } from "react";
import Link from "next/link";
import type { ShowIndexEntry } from "@/types/homepage";

const DISPLAY_COUNT = 6;

function formatDate(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

/** Fisher-Yates shuffle seeded by current date so it changes daily. */
function dailyShuffle<T>(arr: T[]): T[] {
  const copy = [...arr];
  const today = new Date();
  let seed = today.getFullYear() * 10000 + (today.getMonth() + 1) * 100 + today.getDate();
  for (let i = copy.length - 1; i > 0; i--) {
    seed = (seed * 16807 + 0) % 2147483647;
    const j = seed % (i + 1);
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

export default function TopRatedShows({
  shows,
  filterLabel,
}: {
  shows: ShowIndexEntry[];
  filterLabel?: string;
}) {
  const picks = useMemo(() => dailyShuffle(shows).slice(0, DISPLAY_COUNT), [shows]);

  if (picks.length === 0) return null;
  return (
    <section className="mb-6">
      <h4 className="mb-3 text-sm font-bold uppercase tracking-wider text-deadly-title/80">
        Top Rated
        <span className="ml-2 inline-block h-px w-16 align-middle bg-white/20" />
      </h4>
      {filterLabel && (
        <p className="mb-2 text-xs text-deadly-heading">{filterLabel}</p>
      )}
      <div className="space-y-3">
        {picks.map((show) => (
          <Link
            key={show.id}
            href={`/shows/${show.id}`}
            className="block rounded-lg bg-deadly-surface p-3 transition-colors hover:bg-white/10"
          >
            <div className="text-sm font-medium text-white">
              {formatDate(show.d)}
            </div>
            <div className="truncate text-sm text-white/80">{show.v}</div>
            <div className="text-xs text-deadly-heading">
              {show.c}, {show.s}
            </div>
            <div className="mt-1.5 flex items-center gap-3">
              {show.r > 0 && (
                <span className="text-sm">
                  <span className="text-deadly-star">{"\u2605"}</span>{" "}
                  <span className="text-white/60">{show.r.toFixed(1)}</span>
                </span>
              )}
              {show.rc > 0 && (
                <span className="text-xs text-white/40">
                  {show.rc} recording{show.rc !== 1 ? "s" : ""}
                </span>
              )}
            </div>
            {show.sum && (
              <p className="mt-1.5 line-clamp-2 text-xs italic text-white/50">
                {show.sum}
              </p>
            )}
          </Link>
        ))}
      </div>
    </section>
  );
}
