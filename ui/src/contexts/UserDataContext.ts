"use client";

import { createContext, useContext } from "react";
import type { FavoriteShow, FavoriteTrack, ShowReview, UserDataBackupV3, BacklogItem } from "@/types/userdata";

export interface UserDataContextValue {
  data: UserDataBackupV3 | null;
  isLoading: boolean;
  isFavorite: (showId: string) => boolean;
  getReview: (showId: string) => ShowReview | undefined;
  toggleFavorite: (showId: string) => void;
  upsertFavorite: (show: FavoriteShow) => void;
  saveReview: (review: ShowReview) => void;
  removeReview: (showId: string) => void;
  // Favorite songs — identity is the (showId, trackTitle) tuple.
  isFavoriteTrack: (showId: string, trackTitle: string) => boolean;
  toggleFavoriteTrack: (track: FavoriteTrack) => void;
  // Show Queue (backlog) — head first.
  backlog: BacklogItem[];
  isInQueue: (showId: string) => boolean;
  addToQueue: (item: BacklogItem) => void;
  removeFromQueue: (showId: string) => void;
  reorderQueue: (showIds: string[]) => void;
}

export const UserDataContext = createContext<UserDataContextValue | null>(null);

const DEFAULT_VALUE: UserDataContextValue = {
  data: null,
  isLoading: true,
  isFavorite: () => false,
  getReview: () => undefined,
  toggleFavorite: () => {},
  upsertFavorite: () => {},
  saveReview: () => {},
  removeReview: () => {},
  isFavoriteTrack: () => false,
  toggleFavoriteTrack: () => {},
  backlog: [],
  isInQueue: () => false,
  addToQueue: () => {},
  removeFromQueue: () => {},
  reorderQueue: () => {},
};

export function useUserData(): UserDataContextValue {
  const ctx = useContext(UserDataContext);
  return ctx ?? DEFAULT_VALUE;
}
