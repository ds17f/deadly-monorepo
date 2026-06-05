"use client";

import { useState, useEffect, useCallback, useMemo } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { UserDataContext } from "@/contexts/UserDataContext";
import { fetchUserSync, updateFavoriteShow, deleteFavoriteShow, updateFavoriteSong, deleteFavoriteSong, updateReview, deleteReview } from "@/lib/userDataApi";
import * as analytics from "@/lib/analytics";
import { notifyUserDataChanged } from "@/lib/userDataEvents";
import type { UserDataBackupV3, FavoriteShow, FavoriteTrack, ShowReview } from "@/types/userdata";

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

// /api/user/sync returns tombstones (deletedAt set) so mobile can apply remote
// deletes via its outbox. Web has no outbox and just calls DELETE directly, so
// it strips tombstones on ingest and treats sync as a live snapshot.
function stripTombstones(remote: UserDataBackupV3): UserDataBackupV3 {
  const live = <T,>(rows: T[] | undefined): T[] =>
    (rows ?? []).filter((r) => (r as { deletedAt?: number | null }).deletedAt == null);
  return {
    ...remote,
    favorites: {
      shows: live(remote.favorites?.shows),
      tracks: live(remote.favorites?.tracks),
    },
    reviews: live(remote.reviews),
    recordingPreferences: live(remote.recordingPreferences),
    recentShows: remote.recentShows ? live(remote.recentShows) : remote.recentShows,
  };
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

    let cancelled = false;
    const refetch = (reason: string) => {
      console.log("[UserData] Fetching sync for user", user.id, `(${reason})`);
      fetchUserSync()
        .then((raw) => {
          if (cancelled) return;
          const remote = stripTombstones(raw);
          console.log("[UserData] Sync loaded:", remote.favorites.shows.length, "favorites,", remote.reviews.length, "reviews");
          setData(remote);
          saveToStorage(remote);
        })
        .catch((err) => {
          console.error("[UserData] Sync fetch failed:", err);
        });
    };

    refetch("mount");

    // Cheap near-real-time: pick up changes made on other devices when the
    // user returns to this tab. True push (WS) is the planned upgrade — see
    // memory note `project-userdata-realtime-deferred`.
    const onVisible = () => {
      if (document.visibilityState === "visible") refetch("visible");
    };
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("focus", onVisible);

    return () => {
      cancelled = true;
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("focus", onVisible);
    };
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

    // Feeds trending + the dashboard (platform "web"), mirroring mobile.
    analytics.track("feature_use", {
      feature: isCurrentlyFav ? "remove_favorite" : "add_favorite",
      category: "action",
      target_type: "show",
      target_id: showId,
    });

    if (isCurrentlyFav) {
      updateData((prev) => ({
        ...prev,
        favorites: {
          ...prev.favorites,
          shows: prev.favorites.shows.filter((s) => s.showId !== showId),
        },
      }));
      if (user?.id) deleteFavoriteShow(showId).then(notifyUserDataChanged).catch(() => {});
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
      if (user?.id) updateFavoriteShow(showId, fav).then(notifyUserDataChanged).catch(() => {});
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
    if (user?.id) updateFavoriteShow(show.showId, show).then(notifyUserDataChanged).catch(() => {});
  }, [user?.id, updateData]);

  const isFavoriteTrack = useCallback((showId: string, trackTitle: string): boolean => {
    return data?.favorites.tracks.some(
      (t) => t.showId === showId && t.trackTitle === trackTitle,
    ) ?? false;
  }, [data]);

  // Toggle a favorite song by its (showId, trackTitle) identity — optimistic
  // local update plus the matching PUT/DELETE, mirroring toggleFavorite.
  const toggleFavoriteTrack = useCallback((track: FavoriteTrack) => {
    const { showId, trackTitle } = track;
    const isCurrentlyFav = data?.favorites.tracks.some(
      (t) => t.showId === showId && t.trackTitle === trackTitle,
    ) ?? false;

    // Mirror mobile's song-favorite event (target_type "recording_track").
    analytics.track("feature_use", {
      feature: isCurrentlyFav ? "remove_favorite" : "add_favorite",
      category: "action",
      target_type: "recording_track",
      target_id: `${showId}/${track.recordingId ?? ""}/${track.trackNumber ?? 0}`,
    });

    if (isCurrentlyFav) {
      updateData((prev) => ({
        ...prev,
        favorites: {
          ...prev.favorites,
          tracks: prev.favorites.tracks.filter(
            (t) => !(t.showId === showId && t.trackTitle === trackTitle),
          ),
        },
      }));
      if (user?.id) deleteFavoriteSong(showId, trackTitle).then(notifyUserDataChanged).catch(() => {});
    } else {
      const fav: FavoriteTrack = { ...track, updatedAt: Math.floor(Date.now() / 1000) };
      updateData((prev) => ({
        ...prev,
        favorites: {
          ...prev.favorites,
          tracks: [fav, ...prev.favorites.tracks],
        },
      }));
      if (user?.id) updateFavoriteSong(fav).then(notifyUserDataChanged).catch(() => {});
    }
  }, [data, user?.id, updateData]);

  const saveReview = useCallback((review: ShowReview) => {
    updateData((prev) => {
      const reviews = prev.reviews.filter((r) => r.showId !== review.showId);
      return { ...prev, reviews: [review, ...reviews] };
    });
    if (user?.id) {
      const { showId, ...rest } = review;
      updateReview(showId, rest).then(notifyUserDataChanged).catch(() => {});
    }
  }, [user?.id, updateData]);

  const removeReview = useCallback((showId: string) => {
    updateData((prev) => ({
      ...prev,
      reviews: prev.reviews.filter((r) => r.showId !== showId),
    }));
    if (user?.id) deleteReview(showId).then(notifyUserDataChanged).catch(() => {});
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
    isFavoriteTrack,
    toggleFavoriteTrack,
  }), [data, isLoading, isFavorite, getReview, toggleFavorite, upsertFavorite, saveReview, removeReview, isFavoriteTrack, toggleFavoriteTrack]);

  return (
    <UserDataContext.Provider value={value}>
      {children}
    </UserDataContext.Provider>
  );
}
