"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { fetchFavoriteShows } from "@/lib/userDataApi";
import type { FavoriteShow } from "@/types/userdata";
import { useUserData } from "@/contexts/UserDataContext";
import LibraryView from "@/components/library/LibraryView";
import { favoriteToItem } from "@/components/library/libraryItem";
import FavoriteSongsList from "./FavoriteSongsList";

type Section = "shows" | "songs";

// Favorites, split into Shows and Songs sub-views by a segmented control.
// Shows are the full library surface (search / sort / decade filter, list⇄grid,
// per-row Share / Pin / Remove); Songs are a flat list. Toggles for both live
// on the show page / player; these are the read-only collections.
export default function FavoritesTab() {
  const [section, setSection] = useState<Section>("shows");

  return (
    <div>
      <div className="mb-5 inline-flex rounded-lg bg-deadly-surface p-1 text-sm">
        {(["shows", "songs"] as const).map((s) => (
          <button
            key={s}
            type="button"
            onClick={() => setSection(s)}
            aria-pressed={section === s}
            className={`rounded-md px-4 py-1.5 font-medium capitalize transition ${
              section === s
                ? "bg-white/10 text-white"
                : "text-white/50 hover:text-white/80"
            }`}
          >
            {s}
          </button>
        ))}
      </div>

      {section === "shows" ? <FavoriteShows /> : <FavoriteSongsList />}
    </div>
  );
}

// Favorite shows as the full library surface. Reads the enriched
// GET /api/user/favorites/shows; pin & remove route through UserDataContext so
// they persist and other surfaces (hero heart) stay in sync.
function FavoriteShows() {
  const { toggleFavorite, upsertFavorite } = useUserData();
  const [state, setState] = useState<"loading" | "error" | "ready">("loading");
  const [shows, setShows] = useState<FavoriteShow[]>([]);
  // Keep the raw rows so pin can re-PUT the full V3 record.
  const rawRef = useRef<Map<string, FavoriteShow>>(new Map());

  useEffect(() => {
    let cancelled = false;
    fetchFavoriteShows()
      .then((s) => {
        if (cancelled) return;
        rawRef.current = new Map(s.map((x) => [x.showId, x]));
        setShows(s);
        setState("ready");
      })
      .catch(() => {
        if (!cancelled) setState("error");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const items = useMemo(() => shows.map(favoriteToItem), [shows]);

  return (
    <LibraryView
      kind="favorite"
      loadState={state}
      items={items}
      emptyTitle="Favorites"
      emptyHint="Tap the heart on any show — on this site or any device — and it'll show up here."
      actions={{
        canPin: true,
        onPinToggle: (item, next) => {
          const fav = rawRef.current.get(item.showId);
          if (fav) upsertFavorite({ ...fav, isPinned: next });
        },
        remove: {
          label: "Remove from Favorites",
          confirmTitle: "Remove from favorites?",
          confirmMessage: (item) =>
            `"${item.dateLabel}" will be removed from your favorites.`,
          onRemove: (item) => toggleFavorite(item.showId),
        },
      }}
    />
  );
}
