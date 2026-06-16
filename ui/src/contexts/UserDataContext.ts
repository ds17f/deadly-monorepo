"use client";

import { createContext, useContext } from "react";
import type { FavoriteShow, FavoriteTrack, ShowReview, UserDataBackupV3 } from "@/types/userdata";

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
};

export function useUserData(): UserDataContextValue {
  const ctx = useContext(UserDataContext);
  return ctx ?? DEFAULT_VALUE;
}
