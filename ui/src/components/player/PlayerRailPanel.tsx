"use client";

import { usePlayer } from "@/contexts/PlayerContext";
import { useConnect } from "@/contexts/ConnectContext";
import TrackList from "./TrackList";
import RecordingSelector from "./RecordingSelector";
import DeviceList from "@/components/connect/DeviceList";

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

function Header({ title, onClose }: { title: string; onClose: () => void }) {
  return (
    <div className="mb-3 flex items-center justify-between">
      <h3 className="text-xs font-bold uppercase tracking-wider text-deadly-title/80">
        {title}
      </h3>
      <button
        onClick={onClose}
        aria-label="Close panel"
        className="rounded-full p-1 text-white/40 transition-colors hover:text-white/70"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
          <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
        </svg>
      </button>
    </div>
  );
}

// The right-column content the docked player pushes when you open the queue or
// the device picker (Spotify-style: the icon swaps the right pane rather than
// floating a popover). Self-contained — reads the player + connect contexts.
export default function PlayerRailPanel({
  mode,
  onClose,
}: {
  mode: "queue" | "devices";
  onClose: () => void;
}) {
  const {
    activeShow,
    tracks,
    currentTrackIndex,
    status,
    selectedRecording,
    playTrack,
    selectRecording,
    isActiveDevice,
  } = usePlayer();
  const { state: connectState } = useConnect();

  // When remote-controlling, the local audio index doesn't follow remote skips —
  // highlight the server's track + playing state so the queue tracks the session.
  const queueTrackIndex = isActiveDevice ? currentTrackIndex : (connectState?.trackIndex ?? currentTrackIndex);
  const queueStatus = isActiveDevice ? status : (connectState?.playing ? "playing" : "paused");

  if (mode === "devices") {
    return (
      <section className="rounded-lg bg-deadly-surface p-4">
        <Header title="Devices" onClose={onClose} />
        <DeviceList />
      </section>
    );
  }

  // Queue mode
  if (!activeShow) {
    return (
      <section className="rounded-lg bg-deadly-surface p-4">
        <Header title="Queue" onClose={onClose} />
        <p className="text-sm text-white/40">Nothing playing.</p>
      </section>
    );
  }

  const recording = activeShow.recordings.find(
    (r) => r.identifier === selectedRecording,
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
    activeShow.venue + (activeShow.location ? `, ${activeShow.location}` : "");

  return (
    <section className="flex h-full min-h-0 flex-col rounded-lg bg-deadly-surface p-4">
      <Header title="Queue" onClose={onClose} />
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

      {activeShow.recordings.length > 1 && (
        <RecordingSelector
          recordings={activeShow.recordings}
          selectedId={selectedRecording}
          onSelect={selectRecording}
        />
      )}

      <div className="mt-3 flex min-h-0 flex-1 flex-col">
        <TrackList
          tracks={tracks}
          isLoading={false}
          currentTrackIndex={queueTrackIndex}
          status={queueStatus}
          onPlayTrack={playTrack}
          showId={activeShow.showId}
          recordingId={selectedRecording}
          fill
        />
      </div>
    </section>
  );
}
