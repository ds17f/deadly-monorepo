"use client";

import { createContext, useContext } from "react";
import type { Recording } from "@/types/recording";
import type { AiShowReview } from "@/types/show";
import type { ArchiveTrack, PlaybackStatus } from "@/types/player";

export interface ViewedShow {
  showId: string;
  recordings: Recording[];
  bestRecordingId: string | null;
  date: string;
  venue: string;
  location: string;
  // Cover art URL (ticket stub / photo / logo fallback). Optional — remote/
  // parked shows claimed from connect state won't have it.
  image?: string | null;
  // The full AI review, surfaced as rotating factoid cards in the fullscreen
  // view. Only present when played from a show page; remote/parked won't have
  // it (the cards just don't render).
  review?: AiShowReview | null;
}

export interface PlayerContextValue {
  // State
  activeShow: ViewedShow | null;
  viewedShow: ViewedShow | null;
  tracks: ArchiveTrack[] | null;
  currentTrackIndex: number;
  status: PlaybackStatus;
  elapsed: number;
  duration: number;
  selectedRecording: string | null;
  isLoadingTracks: boolean;
  errorMessage: string | null;
  // Output volume, 0..1. Applies to local playback only.
  volume: number;

  // Actions
  setViewedShow: (show: ViewedShow | null) => void;
  setVolume: (volume: number) => void;
  selectRecording: (identifier: string) => void;
  playShow: (show: ViewedShow) => void;
  // Play a show and, once its tracks load, jump to the track matching the
  // given title (falls back to trackNumber). Used by the favorite-songs list.
  playShowTrack: (show: ViewedShow, trackTitle: string, trackNumber?: number | null) => void;
  playRecording: (identifier: string) => void;
  playTrack: (index: number) => void;
  togglePlayPause: () => void;
  nextTrack: () => void;
  prevTrack: () => void;
  seek: (fraction: number) => void;
  close: () => void;
  dismiss: () => void;

  // Autoplay permission
  autoplayBlocked: boolean;
  autoplayInfo: { showDate: string; venue: string; fromDevice: string } | null;
  retryAutoplay: () => void;
  dismissAutoplay: () => void;
}

export const PlayerContext = createContext<PlayerContextValue | null>(null);

export function usePlayer(): PlayerContextValue {
  const ctx = useContext(PlayerContext);
  if (!ctx) {
    throw new Error("usePlayer must be used within a PlayerProvider");
  }
  return ctx;
}
