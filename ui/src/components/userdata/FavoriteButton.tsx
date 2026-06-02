"use client";

import { useUserData } from "@/contexts/UserDataContext";

// Circular icon button, sized to match the play circle so the two read as a
// matched action pair in the show hero.
export default function FavoriteButton({ showId }: { showId: string }) {
  const { isFavorite, toggleFavorite } = useUserData();
  const fav = isFavorite(showId);

  return (
    <button
      onClick={() => toggleFavorite(showId)}
      aria-label={fav ? "Remove from favorites" : "Add to favorites"}
      className="flex h-14 w-14 items-center justify-center rounded-full border border-white/15 transition hover:scale-105 hover:border-white/30"
    >
      <svg
        width="24"
        height="24"
        viewBox="0 0 24 24"
        fill={fav ? "currentColor" : "none"}
        stroke="currentColor"
        strokeWidth="2"
        className={fav ? "text-deadly-accent" : "text-white/60"}
      >
        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
      </svg>
    </button>
  );
}
