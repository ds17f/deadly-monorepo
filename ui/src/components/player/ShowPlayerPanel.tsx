"use client";

import { useEffect, useState } from "react";
import type { Recording } from "@/types/recording";
import { usePlayer } from "@/contexts/PlayerContext";
import RecordingSelector from "./RecordingSelector";
import TrackList from "./TrackList";

interface ShowPlayerPanelProps {
  recordings: Recording[];
  bestRecordingId: string | null;
  showId: string;
  date: string;
  venue: string;
  location: string;
}

export default function ShowPlayerPanel({
  recordings,
  bestRecordingId,
  showId,
  date,
  venue,
  location,
}: ShowPlayerPanelProps) {
  const ctx = usePlayer();

  // Local pending recording for browsing before pressing play
  const [pendingRecordingId, setPendingRecordingId] = useState<string | null>(
    bestRecordingId ?? recordings[0]?.identifier ?? null
  );

  // Register this show as the viewed show (don't clear on unmount —
  // player persists until user explicitly plays a different show)
  useEffect(() => {
    ctx.setViewedShow({ showId, recordings, bestRecordingId, date, venue, location });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showId]);

  if (recordings.length === 0) return null;

  const isActiveShow = ctx.activeShow?.showId === showId;
  const isPlaying = ctx.status !== "idle" && isActiveShow;

  function handlePlay() {
    ctx.playShow({
      showId,
      recordings,
      bestRecordingId: pendingRecordingId,
      date,
      venue,
      location,
    });
  }

  function handleSelectRecording(identifier: string) {
    setPendingRecordingId(identifier);
    if (isPlaying) {
      // If this show is actively playing, switch recording immediately
      ctx.selectRecording(identifier);
    }
  }

  // Show the selected recording ID: if actively playing this show, use context;
  // otherwise use local pending state
  const displaySelectedId = isPlaying
    ? ctx.selectedRecording
    : pendingRecordingId;

  return (
    <>
      {/* Play button — shown when this show is not actively playing */}
      {!isPlaying && pendingRecordingId && (
        <button
          onClick={handlePlay}
          className="mt-3 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-deadly-highlight px-5 py-3 font-semibold text-white transition-opacity hover:opacity-90"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M8 5v14l11-7z" />
          </svg>
          Play on Web
        </button>
      )}

      {/* Error message — only for active show */}
      {isActiveShow && ctx.errorMessage && (
        <div className="mt-3 rounded-lg border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-300">
          <p>{ctx.errorMessage}</p>
          {displaySelectedId && (
            <a
              href={`https://archive.org/details/${displaySelectedId}`}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-1 inline-block text-xs text-red-400 underline hover:text-red-300"
            >
              Try on Archive.org
            </a>
          )}
        </div>
      )}

      {/* Recording selector */}
      <RecordingSelector
        recordings={recordings}
        selectedId={displaySelectedId}
        onSelect={handleSelectRecording}
      />

      {/* Track list — only for active show */}
      {isActiveShow && (
        <TrackList
          tracks={ctx.tracks}
          isLoading={ctx.isLoadingTracks}
          currentTrackIndex={ctx.currentTrackIndex}
          status={ctx.status}
          onPlayTrack={ctx.playTrack}
        />
      )}
    </>
  );
}
