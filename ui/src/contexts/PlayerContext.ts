"use client";

import { createContext, useContext } from "react";
import type { Recording } from "@/types/recording";
import type { AiShowReview } from "@/types/show";
import type { ArchiveTrack, PlaybackStatus } from "@/types/player";
import type { AdvanceMode } from "@/lib/playbackPrefs";

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

  // Connect integration (Connect v2). Derived from the authoritative server
  // ConnectState: this device is "active" when it owns the session and plays
  // audio locally; "remote-controlling" when a session is loaded but another
  // device (or none) is active, so transport routes through the server.
  isActiveDevice: boolean;
  isRemoteControlling: boolean;
  // The action awaiting server confirmation (drives a spinner on remote
  // controls), or null. pendingTransfer is the target deviceId mid-handoff.
  pendingCommand: string | null;
  pendingTransfer: string | null;
  transferTo: (targetDeviceId: string) => void;

  // Actions
  setViewedShow: (show: ViewedShow | null) => void;
  setVolume: (volume: number) => void;
  selectRecording: (identifier: string) => void;
  playShow: (show: ViewedShow) => void;
  // Play a show and, once its tracks load, jump to the track matching the
  // given title (falls back to trackNumber). Used by the favorite-songs list.
  playShowTrack: (show: ViewedShow, trackTitle: string, trackNumber?: number | null) => void;
  // Resolves to the loaded tracks so callers (and the Connect hydration path)
  // can jump to a specific track index once the recording is fetched.
  playRecording: (identifier: string) => Promise<ArchiveTrack[]>;
  playTrack: (index: number) => void;
  // Load the loaded show's tracks without playing, so a parked player can
  // display its playlist for track selection.
  ensureTracks: () => void;
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

  // End-of-show auto-advance (ADR-0010). Non-null while the countdown to the
  // next show is running; nextShow carries its display data so the full player
  // can preview it under a "Next up in Ns" banner.
  autoAdvance: { secondsRemaining: number; nextShow: ViewedShow } | null;
  cancelAutoAdvance: () => void;
  playNextNow: () => void;

  // "Autoplay" toggle (ADR-0010): whether to roll into the next show when one
  // ends. Persisted in localStorage; off by default. `toggleAutoAdvance` cycles
  // the mode (None → Show Queue → Chronological), so the ∞ control matches mobile.
  autoAdvanceEnabled: boolean;
  toggleAutoAdvance: () => void;
  advanceMode: AdvanceMode;
  cycleAdvanceMode: () => void;
  setAdvanceMode: (mode: AdvanceMode) => void;
}

export const PlayerContext = createContext<PlayerContextValue | null>(null);

export function usePlayer(): PlayerContextValue {
  const ctx = useContext(PlayerContext);
  if (!ctx) {
    throw new Error("usePlayer must be used within a PlayerProvider");
  }
  return ctx;
}
