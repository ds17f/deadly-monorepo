"use client";

import { useEffect, useState } from "react";
import { fetchRecentShows } from "@/lib/userDataApi";
import type { RecentShow } from "@/types/userdata";
import ShowRow from "@/components/show/ShowRow";
import { formatShowDate, formatLocation } from "@/components/show/showFormat";

type LoadState =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; shows: RecentShow[] };

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
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <div
            key={i}
            className="h-[76px] animate-pulse rounded-xl border border-white/10 bg-deadly-surface"
          />
        ))}
      </div>
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
    <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {state.shows.map((show) => (
        <li key={show.showId}>
          <ShowRow
            showId={show.showId}
            image={show.image}
            bestRecordingId={show.bestRecordingId}
            date={formatShowDate(show)}
            location={formatLocation(show)}
            venue={show.venue}
            trailing={show.totalPlayCount > 0 ? `${show.totalPlayCount}×` : null}
          />
        </li>
      ))}
    </ul>
  );
}
