"use client";

import Link from "next/link";
import type { ShowIndexEntry } from "@/types/homepage";

function formatDate(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export default function ShowCard({ show }: { show: ShowIndexEntry }) {
  return (
    <Link
      href={`/shows/${show.id}`}
      className="block min-w-[260px] max-w-[300px] shrink-0 rounded-lg bg-deadly-surface p-4 transition-colors hover:bg-white/10"
    >
      <div className="text-sm font-medium text-deadly-red">
        {formatDate(show.d)}
      </div>
      <div className="mt-1 truncate font-medium text-white">{show.v}</div>
      <div className="truncate text-sm text-deadly-heading">
        {show.c}, {show.s}
      </div>
      {show.r > 0 && (
        <div className="mt-2 flex items-center gap-1 text-sm">
          <span className="text-deadly-star">{"\u2605"}</span>
          <span className="text-white/70">{show.r.toFixed(1)}</span>
        </div>
      )}
      {show.sum && (
        <p className="mt-2 line-clamp-2 text-xs text-white/50">{show.sum}</p>
      )}
    </Link>
  );
}
