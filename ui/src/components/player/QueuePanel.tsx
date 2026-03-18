"use client";

import { useEffect } from "react";
import { usePlayer } from "@/contexts/PlayerContext";
import TrackList from "./TrackList";

const SOURCE_COLORS: Record<string, string> = {
  SBD: "bg-deadly-highlight text-white",
  FM: "bg-deadly-highlight text-white",
  Matrix: "bg-deadly-highlight text-white",
  Remaster: "bg-deadly-highlight text-white",
  AUD: "bg-amber-700 text-white",
  UNKNOWN: "bg-white/20 text-white/70",
};

function formatShowDate(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

interface QueuePanelProps {
  onClose: () => void;
}

export default function QueuePanel({ onClose }: QueuePanelProps) {
  const {
    activeShow,
    tracks,
    currentTrackIndex,
    status,
    selectedRecording,
    playTrack,
  } = usePlayer();

  // Close on Escape
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  if (!activeShow) return null;

  const recording = activeShow.recordings.find(
    (r) => r.identifier === selectedRecording
  );
  const sourceLabel = recording
    ? recording.source_type === "UNKNOWN"
      ? "Unknown"
      : recording.source_type
    : null;
  const sourceColors = recording
    ? (SOURCE_COLORS[recording.source_type] ?? SOURCE_COLORS.UNKNOWN)
    : null;

  const venue =
    activeShow.venue +
    (activeShow.location ? `, ${activeShow.location}` : "");

  return (
    <>
      {/* Backdrop — click to close */}
      <div className="fixed inset-0 top-0 z-40" onClick={onClose} />

      {/* Panel */}
      <div className="absolute right-0 top-full z-50 w-full max-w-md border border-t-0 border-white/10 rounded-b-lg bg-deadly-bg shadow-lg shadow-black/40 sm:max-w-sm">
        {/* Show info header */}
        <div className="border-b border-white/10 px-4 py-3">
          <div className="flex items-center gap-2">
            <p className="text-sm font-medium text-white">
              {formatShowDate(activeShow.date)}
            </p>
            {sourceLabel && sourceColors && (
              <span
                className={`inline-block rounded-full px-2 py-0.5 text-[10px] font-medium ${sourceColors}`}
              >
                {sourceLabel}
              </span>
            )}
          </div>
          <p className="text-xs text-white/40">{venue}</p>
        </div>

        {/* Track list */}
        <div className="px-2 pb-2">
          <TrackList
            tracks={tracks}
            isLoading={false}
            currentTrackIndex={currentTrackIndex}
            status={status}
            onPlayTrack={playTrack}
          />
        </div>
      </div>
    </>
  );
}
