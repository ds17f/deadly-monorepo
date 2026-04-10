"use client";

import { useState, useCallback, useEffect, useRef } from "react";
import { usePlayer } from "@/contexts/PlayerContext";
import type { ViewedShow } from "@/contexts/PlayerContext";
import { useConnect } from "@/contexts/ConnectContext";

import QueuePanel from "./QueuePanel";
import AutoplayPrompt from "./AutoplayPrompt";

function formatTime(seconds: number): string {
  if (!isFinite(seconds) || seconds < 0) return "0:00";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

function formatShowDate(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function showLabel(show: ViewedShow): { date: string; venue: string } {
  return {
    date: formatShowDate(show.date),
    venue: show.venue + (show.location ? `, ${show.location}` : ""),
  };
}

export default function HeaderPlayer() {
  const {
    activeShow,
    tracks,
    currentTrackIndex,
    status,
    elapsed,
    duration,
    isLoadingTracks,
    isRemoteControlling,
    pendingCommand,
    togglePlayPause,
    nextTrack,
    prevTrack,
    seek,
  } = usePlayer();

  const { state: connectState } = useConnect();

  const [queueOpen, setQueueOpen] = useState(false);
  const closeQueue = useCallback(() => setQueueOpen(false), []);

  const currentTrack =
    tracks && currentTrackIndex >= 0 ? tracks[currentTrackIndex] : null;
  const hasNext = tracks ? currentTrackIndex < tracks.length - 1 : false;
  const hasPrevious = currentTrackIndex > 0;
  const isActive = (status !== "idle" && currentTrack) || isRemoteControlling;

  // In remote control mode, derive display values from server state
  const remoteTrackTitle = isRemoteControlling && connectState
    ? (connectState.tracks[connectState.trackIndex]?.title ?? null)
    : null;

  // Interpolate remote position for smooth progress bar + time display
  const [interpolatedPositionMs, setInterpolatedPositionMs] = useState(0);
  const rafRef = useRef<number>(0);
  useEffect(() => {
    if (!isRemoteControlling || !connectState) {
      setInterpolatedPositionMs(0);
      return;
    }
    function tick() {
      if (!connectState) return;
      const now = Date.now();
      const posMs = connectState.playing
        ? connectState.positionMs + (now - connectState.positionTs)
        : connectState.positionMs;
      setInterpolatedPositionMs(Math.max(0, posMs));
      rafRef.current = requestAnimationFrame(tick);
    }
    rafRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafRef.current);
  }, [isRemoteControlling, connectState]);
  const remoteProgress = isRemoteControlling && connectState && connectState.durationMs > 0
    ? Math.min(100, (interpolatedPositionMs / connectState.durationMs) * 100)
    : 0;
  const remoteShowInfo = isRemoteControlling && connectState?.date
    ? {
        date: formatShowDate(connectState.date),
        venue: (connectState.venue ?? "") + (connectState.location ? `, ${connectState.location}` : ""),
      }
    : null;

  const progress = isRemoteControlling ? remoteProgress : (duration > 0 ? (elapsed / duration) * 100 : 0);
  const isPlaying = isRemoteControlling
    ? (connectState?.playing ?? false)
    : (status === "playing" || status === "buffering");
  const isLoading = !isRemoteControlling && (status === "loading" || status === "buffering" || isLoadingTracks);
  const showSpinner = pendingCommand !== null || isLoading;

  const showInfo = isRemoteControlling ? remoteShowInfo : (isActive && activeShow ? showLabel(activeShow) : null);
  const trackTitle = isRemoteControlling ? (remoteTrackTitle ?? showInfo?.date ?? "--") : (currentTrack?.title ?? showInfo?.date ?? "--");

  function handleSeek(e: React.MouseEvent<HTMLDivElement>) {
    const rect = e.currentTarget.getBoundingClientRect();
    const fraction = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    seek(fraction);
  }

  return (
    <div className="relative flex flex-1 items-center pl-4">
    <div className="flex flex-1 items-center gap-3 overflow-hidden sm:gap-4">
      {/* Show + track info */}
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline gap-2">
          <p className="truncate text-sm font-medium text-white">
            {trackTitle}
          </p>
          {!isRemoteControlling && tracks && tracks.length > 1 && (
            <span className="flex-shrink-0 text-xs tabular-nums text-white/30">
              {currentTrackIndex + 1}/{tracks.length}
            </span>
          )}
          {isRemoteControlling && connectState && connectState.tracks.length > 1 && (
            <span className="flex-shrink-0 text-xs tabular-nums text-white/30">
              {connectState.trackIndex + 1}/{connectState.tracks.length}
            </span>
          )}
        </div>
        {showInfo ? (
          <p className="truncate text-xs text-white/40">
            {showInfo.date} — {showInfo.venue}
            {isRemoteControlling && connectState?.activeDeviceName && (
              <span className="ml-1 text-white/25">· {connectState.activeDeviceName}</span>
            )}
          </p>
        ) : null}
      </div>

      {/* Seek bar */}
      <div className="flex flex-shrink-0 items-center gap-2">
        <span className="hidden text-[10px] tabular-nums text-white/30 sm:inline">
          {formatTime(isRemoteControlling ? interpolatedPositionMs / 1000 : elapsed)}
        </span>
        <div
          className="h-1.5 w-20 cursor-pointer rounded-full bg-white/10 sm:w-28 md:w-36"
          onClick={handleSeek}
        >
          <div
            className="h-full rounded-full bg-deadly-highlight"
            style={{ width: `${Math.min(progress, 100)}%` }}
          />
        </div>
        <span className="hidden text-[10px] tabular-nums text-white/30 sm:inline">
          {formatTime(isRemoteControlling ? (connectState?.durationMs ?? 0) / 1000 : duration)}
        </span>
      </div>

      {/* Transport controls */}
      <div className="flex flex-shrink-0 items-center gap-0.5">
        <button
          onClick={prevTrack}
          disabled={showSpinner || (!hasPrevious && elapsed < 3)}
          className="rounded-full p-1.5 text-white/60 transition-colors hover:text-white disabled:text-white/20"
          aria-label="Previous track"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <path d="M6 6h2v12H6zm3.5 6 8.5 6V6z" />
          </svg>
        </button>

        <button
          onClick={togglePlayPause}
          disabled={showSpinner}
          className="rounded-full bg-white p-1.5 text-deadly-bg transition-opacity hover:opacity-90 disabled:opacity-50"
          aria-label={isPlaying ? "Pause" : "Play"}
        >
          {showSpinner ? (
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="currentColor"
              className="animate-spin"
            >
              <path d="M12 2a10 10 0 0 1 10 10h-2a8 8 0 0 0-8-8V2z" />
            </svg>
          ) : isPlaying ? (
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M6 19h4V5H6zm8-14v14h4V5z" />
            </svg>
          ) : (
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M8 5v14l11-7z" />
            </svg>
          )}
        </button>

        <button
          onClick={nextTrack}
          disabled={showSpinner || !hasNext}
          className="rounded-full p-1.5 text-white/60 transition-colors hover:text-white disabled:text-white/20"
          aria-label="Next track"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z" />
          </svg>
        </button>
      </div>

      {/* Queue toggle */}
      {isActive && (
        <button
          onClick={() => setQueueOpen((o) => !o)}
          className={`flex-shrink-0 rounded-full p-1.5 transition-colors ${
            queueOpen
              ? "text-deadly-highlight"
              : "text-white/40 hover:text-white/70"
          }`}
          aria-label={queueOpen ? "Close queue" : "Show queue"}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <path d="M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z" />
          </svg>
        </button>
      )}

    </div>

      {/* Queue panel overlay */}
      {queueOpen && <QueuePanel onClose={closeQueue} />}
      <AutoplayPrompt />
    </div>
  );
}
