"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { fetchRecentShows } from "@/lib/userDataApi";
import type { RecentShow } from "@/types/userdata";

type LoadState =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; shows: RecentShow[] };

// showId is the date-prefixed slug (e.g. "1977-05-08-barton-hall-…").
// Pull the leading YYYY-MM-DD off it for a clean header line; the rest of
// the metadata (venue/city) comes from the linked show page for now.
function formatShowDate(showId: string): string {
  const iso = showId.slice(0, 10);
  const d = new Date(`${iso}T00:00:00`);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

export default function RecentTab() {
  const [state, setState] = useState<LoadState>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    fetchRecentShows()
      .then((shows) => {
        if (!cancelled) setState({ status: "ready", shows });
      })
      .catch(() => {
        if (!cancelled) setState({ status: "error" });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (state.status === "loading") {
    return (
      <ul className="space-y-2">
        {Array.from({ length: 5 }).map((_, i) => (
          <li
            key={i}
            className="h-14 animate-pulse rounded-lg border border-white/10 bg-deadly-surface"
          />
        ))}
      </ul>
    );
  }

  if (state.status === "error") {
    return (
      <div className="rounded-lg border border-white/10 bg-deadly-surface p-8 text-center">
        <p className="text-sm text-white/50">
          Couldn&apos;t load your recent shows. Try again in a moment.
        </p>
      </div>
    );
  }

  if (state.shows.length === 0) {
    return (
      <div className="rounded-lg border border-white/10 bg-deadly-surface p-8 text-center">
        <h2 className="text-lg font-medium text-white">Recent shows</h2>
        <p className="mx-auto mt-2 max-w-md text-sm text-white/50">
          Play something on any device to fill this in.
        </p>
      </div>
    );
  }

  return (
    <ul className="space-y-2">
      {state.shows.map((show) => (
        <li key={show.showId}>
          <Link
            href={`/shows/${show.showId}`}
            className="flex items-center justify-between gap-4 rounded-lg border border-white/10 bg-deadly-surface px-4 py-3 transition hover:border-white/30"
          >
            <div className="min-w-0">
              <p className="truncate font-medium text-white">
                {formatShowDate(show.showId)}
              </p>
              {show.totalPlayCount > 0 && (
                <p className="mt-0.5 text-xs text-white/40">
                  Played {show.totalPlayCount}×
                </p>
              )}
            </div>
            <span className="flex-shrink-0 text-sm text-white/40">View show →</span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
