"use client";

import { useEffect, useState } from "react";
import type { Recording } from "@/types/recording";
import { usePlayer } from "@/contexts/PlayerContext";

interface ShowPlayerPanelProps {
  recordings: Recording[];
  bestRecordingId: string | null;
  showId: string;
  date: string;
  venue: string;
  location: string;
}

// A single circular play/pause button. Pressing it loads the show into the
// bottom player, which owns everything else — the track list and switching
// recordings (see QueuePanel). The recording list and tracklist deliberately
// no longer live in the main page view.
export default function ShowPlayerPanel({
  recordings,
  bestRecordingId,
  showId,
  date,
  venue,
  location,
}: ShowPlayerPanelProps) {
  const ctx = usePlayer();

  const [pendingRecordingId] = useState<string | null>(
    bestRecordingId ?? recordings[0]?.identifier ?? null,
  );

  // Register this show as the viewed show (don't clear on unmount — the
  // player persists until the user explicitly plays a different show).
  useEffect(() => {
    ctx.setViewedShow({ showId, recordings, bestRecordingId, date, venue, location });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showId]);

  if (recordings.length === 0) return null;

  const isActiveShow = ctx.activeShow?.showId === showId;
  const isPlaying = isActiveShow && ctx.status === "playing";

  function handleClick() {
    if (isActiveShow) {
      ctx.togglePlayPause();
    } else {
      ctx.playShow({
        showId,
        recordings,
        bestRecordingId: pendingRecordingId,
        date,
        venue,
        location,
      });
    }
  }

  return (
    <button
      onClick={handleClick}
      aria-label={isPlaying ? "Pause" : "Play"}
      className="flex h-14 w-14 items-center justify-center rounded-full bg-deadly-accent text-black shadow-xl transition hover:scale-105"
    >
      {isPlaying ? (
        <svg width="26" height="26" viewBox="0 0 24 24" fill="currentColor">
          <path d="M6 5h4v14H6zM14 5h4v14h-4z" />
        </svg>
      ) : (
        <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor">
          <path d="M8 5v14l11-7z" />
        </svg>
      )}
    </button>
  );
}
