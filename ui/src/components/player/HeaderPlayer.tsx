"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { usePlayer } from "@/contexts/PlayerContext";
import { useConnect } from "@/contexts/ConnectContext";
import type { ViewedShow } from "@/contexts/PlayerContext";
import QueuePanel from "./QueuePanel";
import DevicePicker from "@/components/connect/DevicePicker";

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
    viewedShow,
    tracks,
    currentTrackIndex,
    status,
    elapsed,
    duration,
    selectedRecording,
    togglePlayPause,
    nextTrack,
    prevTrack,
    playTrack,
    seek,
    close,
    playShow,
  } = usePlayer();

  const { isConnected, devices, activeSession, isActiveDevice, claimSession } = useConnect();
  const [queueOpen, setQueueOpen] = useState(false);
  const [devicePickerOpen, setDevicePickerOpen] = useState(false);
  const closeQueue = useCallback(() => setQueueOpen(false), []);
  const closeDevicePicker = useCallback(() => setDevicePickerOpen(false), []);

  const pendingSeekRef = useRef<{ trackIndex: number; positionMs: number } | null>(null);

  // Once tracks load after claiming a session, jump to correct track + seek
  useEffect(() => {
    if (!pendingSeekRef.current || !tracks || tracks.length === 0) return;

    const { trackIndex, positionMs } = pendingSeekRef.current;
    pendingSeekRef.current = null;

    if (trackIndex > 0 && trackIndex < tracks.length) {
      playTrack(trackIndex);
    }

    if (positionMs > 0) {
      const timer = setTimeout(() => {
        window.dispatchEvent(
          new CustomEvent("connect:seek", { detail: { seconds: positionMs / 1000 } })
        );
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [tracks, playTrack]);

  const handleClaimSession = useCallback(() => {
    if (!activeSession) return;

    // Store seek info for after tracks load
    pendingSeekRef.current = {
      trackIndex: activeSession.state.trackIndex,
      positionMs: activeSession.state.positionMs,
    };

    // Claim session on server (will broadcast back with our deviceId)
    claimSession();

    // Load the show from session state
    playShow({
      showId: activeSession.state.showId,
      recordings: [],
      bestRecordingId: activeSession.state.recordingId,
      date: activeSession.state.date ?? "",
      venue: activeSession.state.venue ?? "",
      location: activeSession.state.location ?? "",
    });
  }, [activeSession, playShow, claimSession]);

  const currentTrack =
    tracks && currentTrackIndex >= 0 ? tracks[currentTrackIndex] : null;
  const hasNext = tracks ? currentTrackIndex < tracks.length - 1 : false;
  const hasPrevious = currentTrackIndex > 0;
  const isActive = status !== "idle" && currentTrack;

  // Active session on another device — show "Play here" prompt
  if (activeSession && !isActiveDevice && !isActive) {
    const sessionState = activeSession.state;
    const date = sessionState.date ? formatShowDate(sessionState.date) : "";
    const venue = sessionState.venue
      ? sessionState.venue + (sessionState.location ? `, ${sessionState.location}` : "")
      : "";
    return (
      <div className="flex flex-1 items-center justify-end gap-3 overflow-hidden pl-4">
        <div className="min-w-0 flex-1 text-right">
          <p className="truncate text-sm font-medium text-white/70">
            Playing on {activeSession.deviceName}
          </p>
          <p className="truncate text-xs text-white/40">
            {date}{venue ? ` — ${venue}` : ""}
          </p>
        </div>
        <button
          onClick={handleClaimSession}
          className="flex flex-shrink-0 items-center gap-1.5 rounded-full bg-deadly-highlight px-3.5 py-1.5 text-sm font-semibold text-white transition-opacity hover:opacity-90"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
            <path d="M8 5v14l11-7z" />
          </svg>
          Play here
        </button>
      </div>
    );
  }

  // Nothing playing and no show viewed — hide completely
  if (!isActive && !viewedShow) return null;

  // Idle state: show play button with show info
  if (!isActive && viewedShow) {
    const info = showLabel(viewedShow);
    return (
      <div className="flex flex-1 items-center justify-end gap-3 overflow-hidden pl-4">
        <div className="min-w-0 flex-1 text-right">
          <p className="truncate text-sm font-medium text-white/70">
            {info.date}
          </p>
          <p className="truncate text-xs text-white/40">{info.venue}</p>
        </div>
        <button
          onClick={() => playShow(viewedShow)}
          className="flex flex-shrink-0 items-center gap-1.5 rounded-full bg-deadly-highlight px-3.5 py-1.5 text-sm font-semibold text-white transition-opacity hover:opacity-90"
        >
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="currentColor"
          >
            <path d="M8 5v14l11-7z" />
          </svg>
          Play
        </button>
      </div>
    );
  }

  // Active state: full-width transport with show + track info
  const isPlaying = status === "playing" || status === "buffering";
  const progress = duration > 0 ? (elapsed / duration) * 100 : 0;
  const info = activeShow ? showLabel(activeShow) : null;
  const trackCount = tracks?.length ?? 0;

  // Show "Playing on [device]" when another device owns the session while we're also playing
  const remoteSessionInfo = activeSession && !isActiveDevice ? activeSession.deviceName : null;

  function handleSeek(e: React.MouseEvent<HTMLDivElement>) {
    const rect = e.currentTarget.getBoundingClientRect();
    const fraction = Math.max(
      0,
      Math.min(1, (e.clientX - rect.left) / rect.width)
    );
    seek(fraction);
  }

  return (
    <div className="relative flex flex-1 items-center pl-4">
    <div className="flex flex-1 items-center gap-3 overflow-hidden sm:gap-4">
      {/* Show + track info */}
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline gap-2">
          <p className="truncate text-sm font-medium text-white">
            {currentTrack!.title}
          </p>
          {trackCount > 1 && (
            <span className="flex-shrink-0 text-xs tabular-nums text-white/30">
              {currentTrackIndex + 1}/{trackCount}
            </span>
          )}
        </div>
        {remoteSessionInfo ? (
          <p className="truncate text-xs text-deadly-highlight">
            Playing on {remoteSessionInfo}
          </p>
        ) : info ? (
          <p className="truncate text-xs text-white/40">
            {info.date} — {info.venue}
          </p>
        ) : null}
      </div>

      {/* Seek bar */}
      <div className="flex flex-shrink-0 items-center gap-2">
        <span className="hidden text-[10px] tabular-nums text-white/30 sm:inline">
          {formatTime(elapsed)}
        </span>
        <div
          className="h-1.5 w-20 cursor-pointer rounded-full bg-white/10 sm:w-28 md:w-36"
          onClick={handleSeek}
        >
          <div
            className="h-full rounded-full bg-deadly-highlight transition-[width] duration-150"
            style={{ width: `${progress}%` }}
          />
        </div>
        <span className="hidden text-[10px] tabular-nums text-white/30 sm:inline">
          {formatTime(duration)}
        </span>
      </div>

      {/* Transport controls */}
      <div className="flex flex-shrink-0 items-center gap-0.5">
        <button
          onClick={prevTrack}
          disabled={!hasPrevious && elapsed < 3}
          className="rounded-full p-1.5 text-white/60 transition-colors hover:text-white disabled:text-white/20"
          aria-label="Previous track"
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="currentColor"
          >
            <path d="M6 6h2v12H6zm3.5 6 8.5 6V6z" />
          </svg>
        </button>

        <button
          onClick={togglePlayPause}
          disabled={status === "loading"}
          className="rounded-full bg-white p-1.5 text-deadly-bg transition-opacity hover:opacity-90 disabled:opacity-50"
          aria-label={isPlaying ? "Pause" : "Play"}
        >
          {status === "loading" || status === "buffering" ? (
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
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="currentColor"
            >
              <path d="M6 19h4V5H6zm8-14v14h4V5z" />
            </svg>
          ) : (
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="currentColor"
            >
              <path d="M8 5v14l11-7z" />
            </svg>
          )}
        </button>

        <button
          onClick={nextTrack}
          disabled={!hasNext}
          className="rounded-full p-1.5 text-white/60 transition-colors hover:text-white disabled:text-white/20"
          aria-label="Next track"
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="currentColor"
          >
            <path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z" />
          </svg>
        </button>
      </div>

      {/* Queue toggle */}
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

      {/* Close button */}
      <button
        onClick={close}
        className="flex-shrink-0 rounded-full p-1.5 text-white/25 transition-colors hover:text-white/50"
        aria-label="Close player"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
          <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
        </svg>
      </button>

    </div>

      {/* Device picker — outside overflow-hidden so dropdown isn't clipped */}
      {isConnected && devices.length > 1 && (
        <div className="relative flex-shrink-0">
          <button
            onClick={() => setDevicePickerOpen((o) => !o)}
            className={`rounded-full p-1.5 transition-colors ${
              devicePickerOpen
                ? "text-deadly-highlight"
                : "text-white/40 hover:text-white/70"
            }`}
            aria-label="Connect to device"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M1 18v3h4v-3H1zm0-6v3h8v-3H1zm0-6v3h12V6H1zm20 12.59L17.42 15 16 16.41 21 21.41l5-5L24.59 15 21 18.59z" />
            </svg>
          </button>
          {devicePickerOpen && (
            <DevicePicker
              currentState={
                activeShow && selectedRecording
                  ? {
                      showId: activeShow.showId,
                      recordingId: selectedRecording,
                      trackIndex: currentTrackIndex,
                      positionMs: Math.floor(elapsed * 1000),
                      status: status === "playing" ? "playing" : "paused",
                      date: activeShow.date,
                      venue: activeShow.venue,
                      location: activeShow.location,
                    }
                  : null
              }
              onClose={closeDevicePicker}
            />
          )}
        </div>
      )}

      {/* Queue panel overlay */}
      {queueOpen && <QueuePanel onClose={closeQueue} />}
    </div>
  );
}
