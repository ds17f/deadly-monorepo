"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { usePlayer } from "@/contexts/PlayerContext";
import type { ViewedShow } from "@/contexts/PlayerContext";
import { useConnect } from "@/contexts/ConnectContext";

import { useInterpolatedPosition } from "@/hooks/useInterpolatedPosition";
import QueuePanel from "./QueuePanel";
import AutoplayPrompt from "./AutoplayPrompt";
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
    tracks,
    currentTrackIndex,
    status,
    elapsed,
    duration,
    selectedRecording,
    isLoadingTracks,
    togglePlayPause,
    nextTrack,
    prevTrack,
    playTrack,
    seek,
    playShow,
  } = usePlayer();

  const { isConnected, userState, isActiveDevice, claimSession, sendCommand } = useConnect();
  const [queueOpen, setQueueOpen] = useState(false);
  const [devicePickerOpen, setDevicePickerOpen] = useState(false);
  const closeQueue = useCallback(() => setQueueOpen(false), []);
  const closeDevicePicker = useCallback(() => setDevicePickerOpen(false), []);

  const pendingSeekRef = useRef<{ trackIndex: number; positionMs: number } | null>(null);

  const isLocalPlayback = status === "playing" || status === "paused" || status === "buffering" || status === "loading";
  const interpolatedMs = useInterpolatedPosition(userState, isLocalPlayback, elapsed);

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
    if (!userState) return;

    // Store seek info for after tracks load
    pendingSeekRef.current = {
      trackIndex: userState.trackIndex,
      positionMs: userState.positionMs,
    };

    // Claim session on server (will broadcast back with our deviceId)
    claimSession();

    // Load the show from user state
    playShow({
      showId: userState.showId,
      recordings: [],
      bestRecordingId: userState.recordingId,
      date: userState.date ?? "",
      venue: userState.venue ?? "",
      location: userState.location ?? "",
    });
  }, [userState, playShow, claimSession]);

  const handleClaimAndNext = useCallback(() => {
    if (!userState) return;
    pendingSeekRef.current = { trackIndex: userState.trackIndex + 1, positionMs: 0 };
    claimSession();
    playShow({
      showId: userState.showId,
      recordings: [],
      bestRecordingId: userState.recordingId,
      date: userState.date ?? "",
      venue: userState.venue ?? "",
      location: userState.location ?? "",
    });
  }, [userState, playShow, claimSession]);

  const handleClaimAndPrev = useCallback(() => {
    if (!userState) return;
    pendingSeekRef.current = { trackIndex: Math.max(0, userState.trackIndex - 1), positionMs: 0 };
    claimSession();
    playShow({
      showId: userState.showId,
      recordings: [],
      bestRecordingId: userState.recordingId,
      date: userState.date ?? "",
      venue: userState.venue ?? "",
      location: userState.location ?? "",
    });
  }, [userState, playShow, claimSession]);

  const currentTrack =
    tracks && currentTrackIndex >= 0 ? tracks[currentTrackIndex] : null;
  const hasNext = tracks ? currentTrackIndex < tracks.length - 1 : false;
  const hasPrevious = currentTrackIndex > 0;
  const isActive = status !== "idle" && currentTrack;

  // Determine if another device is the active player
  const isRemoteActive = !!(userState && userState.activeDeviceId && !isActiveDevice);
  // Parked: userState exists, no active device, not locally playing
  const isParked = !!(userState && !userState.activeDeviceId && !isActive);

  // Remote track boundary checks (from server-managed track list)
  const remoteTrackCount = userState?.tracks?.length ?? 0;
  const remoteHasNext = remoteTrackCount > 0 && (userState?.trackIndex ?? 0) < remoteTrackCount - 1;
  const remoteHasPrevious = (userState?.trackIndex ?? 0) > 0;

  // Remote control helpers — send commands through the server (state-mediated)
  const remoteTogglePlayPause = useCallback(() => {
    sendCommand(userState?.isPlaying ? "pause" : "play");
  }, [userState, sendCommand]);

  const remoteNext = useCallback(() => {
    sendCommand("next");
  }, [sendCommand]);

  const remotePrev = useCallback(() => {
    if (interpolatedMs > 3000 || !remoteHasPrevious) {
      // Restart current track (matches local player behavior)
      sendCommand("seek", 0);
    } else {
      sendCommand("prev");
    }
  }, [sendCommand, interpolatedMs, remoteHasPrevious]);

  const remoteSeek = useCallback((fraction: number) => {
    if (!userState?.durationMs) return;
    const seekMs = Math.floor(fraction * userState.durationMs);
    sendCommand("seek", seekMs);
  }, [userState, sendCommand]);

  // Unified transport actions — local vs remote vs parked
  const handleTogglePlayPause = isActive && isActiveDevice
    ? togglePlayPause
    : isRemoteActive
      ? remoteTogglePlayPause
      : handleClaimSession; // parked — claim and play

  const handleNext = isActive && isActiveDevice ? nextTrack : isRemoteActive ? remoteNext : isParked ? handleClaimAndNext : undefined;
  const handlePrev = isActive && isActiveDevice ? prevTrack : isRemoteActive ? remotePrev : isParked ? handleClaimAndPrev : undefined;

  function handleSeek(e: React.MouseEvent<HTMLDivElement>) {
    const rect = e.currentTarget.getBoundingClientRect();
    const fraction = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    if (isActive && isActiveDevice) {
      seek(fraction);
    } else if (isRemoteActive || isParked) {
      remoteSeek(fraction);
    }
  }

  // Show info for display
  const displayElapsed = isActive && isActiveDevice ? elapsed : interpolatedMs / 1000;
  const displayDuration = isActive && isActiveDevice
    ? duration
    : (userState?.durationMs ?? 0) / 1000;
  const progress = displayDuration > 0 ? (displayElapsed / displayDuration) * 100 : 0;

  // Determine what show info to display
  const showInfo = isActive && activeShow
    ? showLabel(activeShow)
    : userState?.date
      ? { date: formatShowDate(userState.date), venue: (userState.venue ?? "") + (userState.location ? `, ${userState.location}` : "") }
      : null;

  // Track title for display
  const displayTrackTitle = isActive && currentTrack
    ? currentTrack.title
    : userState?.trackTitle ?? null;

  const displayTrackCount = isActive ? (tracks?.length ?? 0) : 0;
  const displayTrackIndex = isActive ? currentTrackIndex : (userState?.trackIndex ?? 0);

  // Determine playing state for button icon
  const displayIsPlaying = isActive
    ? (status === "playing" || status === "buffering")
    : (userState?.isPlaying ?? false);

  const isLoading = status === "loading" || status === "buffering";

  // ── Active / Remote active / Parked / Idle: unified full transport UI ──
  // Subtitle line: show device info when remote, show info when local
  const subtitleLine = isRemoteActive
    ? `${userState?.isPlaying ? "Playing" : "Paused"} on ${userState?.activeDeviceName}`
    : showInfo
      ? `${showInfo.date} — ${showInfo.venue}`
      : null;

  return (
    <div className="relative flex flex-1 items-center pl-4">
    <div className="flex flex-1 items-center gap-3 overflow-hidden sm:gap-4">
      {/* Show + track info */}
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline gap-2">
          <p className="truncate text-sm font-medium text-white">
            {displayTrackTitle ?? showInfo?.date ?? "--"}
          </p>
          {displayTrackCount > 1 && (
            <span className="flex-shrink-0 text-xs tabular-nums text-white/30">
              {displayTrackIndex + 1}/{displayTrackCount}
            </span>
          )}
        </div>
        {isRemoteActive ? (
          <p className="truncate text-xs text-deadly-highlight">
            {subtitleLine}
          </p>
        ) : showInfo ? (
          <p className="truncate text-xs text-white/40">
            {showInfo.date} — {showInfo.venue}
          </p>
        ) : null}
      </div>

      {/* Seek bar */}
      <div className="flex flex-shrink-0 items-center gap-2">
        <span className="hidden text-[10px] tabular-nums text-white/30 sm:inline">
          {formatTime(displayElapsed)}
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
          {formatTime(displayDuration)}
        </span>
      </div>

      {/* Transport controls */}
      <div className="flex flex-shrink-0 items-center gap-0.5">
        <button
          onClick={handlePrev}
          disabled={!handlePrev || !!(isActive && isActiveDevice && !hasPrevious && elapsed < 3) || !!(isRemoteActive && !remoteHasPrevious && interpolatedMs < 3000)}
          className="rounded-full p-1.5 text-white/60 transition-colors hover:text-white disabled:text-white/20"
          aria-label="Previous track"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <path d="M6 6h2v12H6zm3.5 6 8.5 6V6z" />
          </svg>
        </button>

        <button
          onClick={handleTogglePlayPause}
          disabled={isLoadingTracks || !!(isActive && isActiveDevice && isLoading)}
          className="rounded-full bg-white p-1.5 text-deadly-bg transition-opacity hover:opacity-90 disabled:opacity-50"
          aria-label={displayIsPlaying ? "Pause" : "Play"}
        >
          {isLoadingTracks || (isActive && isActiveDevice && isLoading) ? (
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="currentColor"
              className="animate-spin"
            >
              <path d="M12 2a10 10 0 0 1 10 10h-2a8 8 0 0 0-8-8V2z" />
            </svg>
          ) : displayIsPlaying ? (
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
          onClick={handleNext}
          disabled={!handleNext || !!(isActive && isActiveDevice && !hasNext) || (isRemoteActive && !remoteHasNext)}
          className="rounded-full p-1.5 text-white/60 transition-colors hover:text-white disabled:text-white/20"
          aria-label="Next track"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z" />
          </svg>
        </button>
      </div>

      {/* Queue toggle — only when local playback */}
      {isActive && isActiveDevice && (
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

      {/* Device picker — visible in all player states when connected */}
      {isConnected && (
        <div className="relative flex-shrink-0">
          <button
            onClick={() => setDevicePickerOpen((o) => !o)}
            className={`rounded-full p-1.5 transition-colors ${
              devicePickerOpen
                ? "text-deadly-highlight"
                : isRemoteActive
                  ? "text-deadly-highlight/70 hover:text-deadly-highlight"
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
                isActive && isActiveDevice && activeShow && selectedRecording
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
                  : userState
                    ? {
                        showId: userState.showId,
                        recordingId: userState.recordingId,
                        trackIndex: userState.trackIndex,
                        positionMs: Math.floor(interpolatedMs),
                        durationMs: userState.durationMs,
                        trackTitle: userState.trackTitle,
                        status: userState.isPlaying ? "playing" : "paused",
                        date: userState.date,
                        venue: userState.venue,
                        location: userState.location,
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
      <AutoplayPrompt />
    </div>
  );
}
