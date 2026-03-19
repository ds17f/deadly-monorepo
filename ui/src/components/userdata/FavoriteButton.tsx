"use client";

import { useUserData } from "@/contexts/UserDataContext";

export default function FavoriteButton({ showId }: { showId: string }) {
  const { isFavorite, toggleFavorite } = useUserData();
  const fav = isFavorite(showId);

  return (
    <button
      onClick={() => toggleFavorite(showId)}
      className="group flex items-center gap-1.5 rounded-full border border-white/10 px-3 py-1.5 text-sm transition-colors hover:border-white/20"
      aria-label={fav ? "Remove from favorites" : "Add to favorites"}
    >
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill={fav ? "currentColor" : "none"}
        stroke="currentColor"
        strokeWidth="2"
        className={fav ? "text-red-400" : "text-white/40 group-hover:text-white/70"}
      >
        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
      </svg>
      <span className={fav ? "text-red-400" : "text-white/50 group-hover:text-white/70"}>
        {fav ? "Favorited" : "Favorite"}
      </span>
    </button>
  );
}
