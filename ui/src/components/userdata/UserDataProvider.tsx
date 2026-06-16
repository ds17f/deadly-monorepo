"use client";

import { useState, useEffect, useCallback, useMemo } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { UserDataContext } from "@/contexts/UserDataContext";
import { fetchUserSync, updateFavoriteShow, deleteFavoriteShow, updateFavoriteSong, deleteFavoriteSong, updateReview, deleteReview, fetchBacklog, updateBacklogItem, deleteBacklogItem, reorderBacklog } from "@/lib/userDataApi";
import * as analytics from "@/lib/analytics";
import { notifyUserDataChanged } from "@/lib/userDataEvents";
import type { UserDataBackupV3, FavoriteShow, FavoriteTrack, ShowReview, BacklogItem } from "@/types/userdata";

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
  // Show Queue (backlog) — server-backed, fetched separately from /sync because
  // its GET is enriched with show display metadata. Head first.
  const [backlog, setBacklog] = useState<BacklogItem[]>([]);

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
      fetchBacklog()
        .then((rows) => { if (!cancelled) setBacklog(rows); })
        .catch((err) => { console.error("[UserData] Backlog fetch failed:", err); });
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

  const isInQueue = useCallback((showId: string): boolean => {
    return backlog.some((b) => b.showId === showId);
  }, [backlog]);

  // Append to the bottom (optimistic + PUT). The caller passes display meta so
  // the queue renders immediately; the next focus-refetch reconciles.
  const addToQueue = useCallback((item: BacklogItem) => {
    if (backlog.some((b) => b.showId === item.showId)) return;
    const now = Math.floor(Date.now() / 1000);
    const maxPos = backlog.reduce((m, b) => Math.max(m, b.position), -1);
    const row: BacklogItem = { ...item, position: maxPos + 1, addedAt: item.addedAt || now, updatedAt: now };
    setBacklog((prev) => [...prev, row]);
    analytics.track("feature_use", {
      feature: "add_to_show_queue", category: "action", target_type: "show", target_id: item.showId,
    });
    if (user?.id) updateBacklogItem(item.showId, { position: row.position, addedAt: row.addedAt, updatedAt: now }).then(notifyUserDataChanged).catch(() => {});
  }, [backlog, user?.id]);

  const removeFromQueue = useCallback((showId: string) => {
    setBacklog((prev) => prev.filter((b) => b.showId !== showId));
    if (user?.id) deleteBacklogItem(showId).then(notifyUserDataChanged).catch(() => {});
  }, [user?.id]);

  // Rewrite order to exactly `showIds` (drag-to-reorder). Reindex positions to
  // match, then PUT the bulk order.
  const reorderQueue = useCallback((showIds: string[]) => {
    setBacklog((prev) => {
      const byId = new Map(prev.map((b) => [b.showId, b]));
      return showIds
        .map((id, i) => { const b = byId.get(id); return b ? { ...b, position: i } : undefined; })
        .filter((b): b is BacklogItem => b != null);
    });
    if (user?.id) reorderBacklog(showIds).then(notifyUserDataChanged).catch(() => {});
  }, [user?.id]);

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
    backlog,
    isInQueue,
    addToQueue,
    removeFromQueue,
    reorderQueue,
  }), [data, isLoading, isFavorite, getReview, toggleFavorite, upsertFavorite, saveReview, removeReview, isFavoriteTrack, toggleFavoriteTrack, backlog, isInQueue, addToQueue, removeFromQueue, reorderQueue]);

  return (
    <UserDataContext.Provider value={value}>
      {children}
    </UserDataContext.Provider>
  );
}
