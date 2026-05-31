"use client";

import { useEffect, useState } from "react";
import { fetchFavoriteShows } from "@/lib/userDataApi";
import type { FavoriteShow } from "@/types/userdata";
import ShowRow from "./ShowRow";
import { formatShowDate, formatLocation } from "./showFormat";

type LoadState =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; shows: FavoriteShow[] };

// Pinned first, then most-recently-favorited.
function sortFavorites(shows: FavoriteShow[]): FavoriteShow[] {
  return [...shows].sort((a, b) => {
    if (a.isPinned !== b.isPinned) return a.isPinned ? -1 : 1;
    return b.addedAt - a.addedAt;
  });
}

export default function FavoritesTab() {
  const [state, setState] = useState<LoadState>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    fetchFavoriteShows()
      .then((shows) => {
        if (!cancelled) setState({ status: "ready", shows: sortFavorites(shows) });
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
          Couldn&apos;t load your favorites. Try again in a moment.
        </p>
      </div>
    );
  }

  if (state.shows.length === 0) {
    return (
      <div className="rounded-lg border border-white/10 bg-deadly-surface p-8 text-center">
        <h2 className="text-lg font-medium text-white">Favorites</h2>
        <p className="mx-auto mt-2 max-w-md text-sm text-white/50">
          Tap the heart on any show — on this site or any device — and it&apos;ll
          show up here.
        </p>
      </div>
    );
  }

  return (
    <ul className="space-y-2">
      {state.shows.map((show) => {
        const primary = show.venue ?? formatShowDate(show);
        const secondary = [
          show.venue ? formatShowDate(show) : null,
          formatLocation(show),
          show.isPinned ? "Pinned" : null,
        ]
          .filter(Boolean)
          .join(" · ");
        return (
          <li key={show.showId}>
            <ShowRow showId={show.showId} primary={primary} secondary={secondary} />
          </li>
        );
      })}
    </ul>
  );
}
