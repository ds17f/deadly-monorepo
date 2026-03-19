"use client";

import { useState, useEffect, useCallback, useMemo } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { UserDataContext } from "@/contexts/UserDataContext";
import { fetchUserSync, updateFavoriteShow, deleteFavoriteShow, updateReview, deleteReview } from "@/lib/userDataApi";
import type { UserDataBackupV3, FavoriteShow, ShowReview } from "@/types/userdata";

const STORAGE_KEY = "deadly_userdata";

function loadFromStorage(): UserDataBackupV3 | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as UserDataBackupV3) : null;
  } catch {
    return null;
  }
}

function saveToStorage(data: UserDataBackupV3): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch { /* storage full — ignore */ }
}

function emptyBackup(): UserDataBackupV3 {
  return {
    version: 3,
    exportedAt: 0,
    app: "deadly-web",
    favorites: { shows: [], tracks: [] },
    reviews: [],
    recordingPreferences: [],
    settings: null,
  };
}

export default function UserDataProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [data, setData] = useState<UserDataBackupV3 | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Load from localStorage immediately, then fetch from API if authed
  useEffect(() => {
    const local = loadFromStorage();
    setData(local ?? emptyBackup());
    setIsLoading(false);
  }, []);

  useEffect(() => {
    if (!user?.id) {
      console.log("[UserData] No user.id, skipping API sync");
      return;
    }
    console.log("[UserData] Fetching sync for user", user.id);
    fetchUserSync()
      .then((remote) => {
        console.log("[UserData] Sync loaded:", remote.favorites.shows.length, "favorites,", remote.reviews.length, "reviews");
        setData(remote);
        saveToStorage(remote);
      })
      .catch((err) => {
        console.error("[UserData] Sync fetch failed:", err);
      });
  }, [user?.id]);

  const updateData = useCallback((updater: (prev: UserDataBackupV3) => UserDataBackupV3) => {
    setData((prev) => {
      const next = updater(prev ?? emptyBackup());
      saveToStorage(next);
      return next;
    });
  }, []);

  const isFavorite = useCallback((showId: string): boolean => {
    return data?.favorites.shows.some((s) => s.showId === showId) ?? false;
  }, [data]);

  const getReview = useCallback((showId: string): ShowReview | undefined => {
    return data?.reviews.find((r) => r.showId === showId);
  }, [data]);

  const toggleFavorite = useCallback((showId: string) => {
    const isCurrentlyFav = data?.favorites.shows.some((s) => s.showId === showId) ?? false;

    if (isCurrentlyFav) {
      updateData((prev) => ({
        ...prev,
        favorites: {
          ...prev.favorites,
          shows: prev.favorites.shows.filter((s) => s.showId !== showId),
        },
      }));
      if (user?.id) deleteFavoriteShow(showId).catch(() => {});
    } else {
      const fav: FavoriteShow = {
        showId,
        addedAt: Math.floor(Date.now() / 1000),
        isPinned: false,
      };
      updateData((prev) => ({
        ...prev,
        favorites: {
          ...prev.favorites,
          shows: [fav, ...prev.favorites.shows],
        },
      }));
      if (user?.id) updateFavoriteShow(showId, fav).catch(() => {});
    }
  }, [data, user?.id, updateData]);

  const upsertFavorite = useCallback((show: FavoriteShow) => {
    updateData((prev) => {
      const shows = prev.favorites.shows.filter((s) => s.showId !== show.showId);
      return {
        ...prev,
        favorites: { ...prev.favorites, shows: [show, ...shows] },
      };
    });
    if (user?.id) updateFavoriteShow(show.showId, show).catch(() => {});
  }, [user?.id, updateData]);

  const saveReview = useCallback((review: ShowReview) => {
    updateData((prev) => {
      const reviews = prev.reviews.filter((r) => r.showId !== review.showId);
      return { ...prev, reviews: [review, ...reviews] };
    });
    if (user?.id) {
      const { showId, ...rest } = review;
      updateReview(showId, rest).catch(() => {});
    }
  }, [user?.id, updateData]);

  const removeReview = useCallback((showId: string) => {
    updateData((prev) => ({
      ...prev,
      reviews: prev.reviews.filter((r) => r.showId !== showId),
    }));
    if (user?.id) deleteReview(showId).catch(() => {});
  }, [user?.id, updateData]);

  const value = useMemo(() => ({
    data,
    isLoading,
    isFavorite,
    getReview,
    toggleFavorite,
    upsertFavorite,
    saveReview,
    removeReview,
  }), [data, isLoading, isFavorite, getReview, toggleFavorite, upsertFavorite, saveReview, removeReview]);

  return (
    <UserDataContext.Provider value={value}>
      {children}
    </UserDataContext.Provider>
  );
}
