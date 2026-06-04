"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { fetchFavoriteSongs } from "@/lib/userDataApi";
import type { FavoriteTrack } from "@/types/userdata";
import { useUserData } from "@/contexts/UserDataContext";
import { formatShowDate, formatLocation } from "@/components/show/showFormat";

// Favorite songs as a flat list. The heart toggle itself lives on track rows
// in the player (TrackList); this is the read-only collection, mirroring the
// shows list. Each row links to the song's show. Reads the enriched
// GET /api/user/favorites/songs (date/venue/city merged from the catalog);
// remove routes through UserDataContext so the player hearts stay in sync.
export default function FavoriteSongsList() {
  const { toggleFavoriteTrack } = useUserData();
  const [state, setState] = useState<"loading" | "error" | "ready">("loading");
  const [songs, setSongs] = useState<FavoriteTrack[]>([]);

  useEffect(() => {
    let cancelled = false;
    fetchFavoriteSongs()
      .then((s) => {
        if (cancelled) return;
        setSongs(s);
        setState("ready");
      })
      .catch(() => {
        if (!cancelled) setState("error");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function remove(track: FavoriteTrack) {
    setSongs((prev) =>
      prev.filter(
        (t) => !(t.showId === track.showId && t.trackTitle === track.trackTitle),
      ),
    );
    toggleFavoriteTrack(track);
  }

  const rows = useMemo(
    () =>
      songs.map((s) => {
        const venueOrCity = s.venue ?? formatLocation(s);
        return {
          ...s,
          subtitle: venueOrCity
            ? `${formatShowDate(s)} · ${venueOrCity}`
            : formatShowDate(s),
        };
      }),
    [songs],
  );

  if (state === "loading") {
    return (
      <ul className="space-y-2.5">
        {Array.from({ length: 8 }).map((_, i) => (
          <li
            key={i}
            className="h-[60px] animate-pulse rounded-xl bg-deadly-surface"
          />
        ))}
      </ul>
    );
  }

  if (state === "error") {
    return (
      <div className="rounded-lg border border-white/10 bg-deadly-surface p-8 text-center">
        <p className="text-sm text-white/50">
          Couldn&apos;t load this list. Try again in a moment.
        </p>
      </div>
    );
  }

  if (rows.length === 0) {
    return (
      <div className="rounded-lg border border-white/10 bg-deadly-surface p-8 text-center">
        <h2 className="text-lg font-medium text-white">Favorite songs</h2>
        <p className="mx-auto mt-2 max-w-md text-sm text-white/50">
          Tap the heart next to any track in the player — on this site or any
          device — and it&apos;ll show up here.
        </p>
      </div>
    );
  }

  return (
    <ul className="space-y-2.5">
      {rows.map((s) => (
        <li
          key={`${s.showId}::${s.trackTitle}`}
          className="flex items-center gap-4 rounded-xl border border-white/10 bg-deadly-surface p-3 transition hover:border-white/30"
        >
          <Link
            href={`/shows/${s.showId}`}
            className="min-w-0 flex-1"
          >
            <p className="truncate text-base font-semibold text-white">
              {s.trackTitle}
            </p>
            <p className="truncate text-sm text-white/50">{s.subtitle}</p>
          </Link>
          <button
            type="button"
            onClick={() => remove(s)}
            aria-label="Remove from favorite songs"
            className="flex-shrink-0 rounded-full p-2 text-deadly-highlight transition hover:bg-white/10"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M12 21s-7.5-4.9-10-9.2C.3 8.3 2 5 5.2 5c1.9 0 3.3 1 3.8 2.4C9.5 6 11 5 12.8 5 16 5 17.7 8.3 16 11.8 13.5 16.1 12 21 12 21z" />
            </svg>
          </button>
        </li>
      ))}
    </ul>
  );
}
