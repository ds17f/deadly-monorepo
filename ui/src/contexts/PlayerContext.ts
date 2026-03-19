"use client";

import { createContext, useContext } from "react";
import type { Recording } from "@/types/recording";
import type { ArchiveTrack, PlaybackStatus } from "@/types/player";

export interface ViewedShow {
  showId: string;
  recordings: Recording[];
  bestRecordingId: string | null;
  date: string;
  venue: string;
  location: string;
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

  // Actions
  setViewedShow: (show: ViewedShow | null) => void;
  selectRecording: (identifier: string) => void;
  playShow: (show: ViewedShow) => void;
  playRecording: (identifier: string) => void;
  playTrack: (index: number) => void;
  togglePlayPause: () => void;
  nextTrack: () => void;
  prevTrack: () => void;
  seek: (fraction: number) => void;
  close: () => void;
  dismiss: () => void;
}

export const PlayerContext = createContext<PlayerContextValue | null>(null);

export function usePlayer(): PlayerContextValue {
  const ctx = useContext(PlayerContext);
  if (!ctx) {
    throw new Error("usePlayer must be used within a PlayerProvider");
  }
  return ctx;
}
