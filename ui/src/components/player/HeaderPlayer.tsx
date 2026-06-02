"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { usePlayer } from "@/contexts/PlayerContext";
import type { ViewedShow } from "@/contexts/PlayerContext";
import { useConnect } from "@/contexts/ConnectContext";

import { useInterpolatedPosition } from "@/hooks/useInterpolatedPosition";
import QueuePanel from "./QueuePanel";
import AutoplayPrompt from "./AutoplayPrompt";
import RecordingSelector from "./RecordingSelector";
import TrackList from "./TrackList";
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
    selectRecording,
    isLoadingTracks,
    togglePlayPause,
    nextTrack,
    prevTrack,
    playTrack,
    seek,
    playShow,
    dismiss,
  } = usePlayer();

  const { isConnected, userState, isActiveDevice, claimSession, sendCommand } = useConnect();
  const [queueOpen, setQueueOpen] = useState(false);
  const [devicePickerOpen, setDevicePickerOpen] = useState(false);
  // The mini bar expands into a full-screen "now playing" sheet that slides
  // up; slide it back down to collapse.
  const [expanded, setExpanded] = useState(false);
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

  // Cover art (from the viewed/active show); logo when absent (e.g. remote).
  const art = activeShow?.image ?? null;
  const artIsLogo = !art || art.endsWith("/logo.png");
  const artSrc = art ?? "/logo.png";
  const showLoaded = isActive || isParked || isRemoteActive;

  return (
    <div className="relative flex flex-1 items-center pl-4">
    <div className="flex flex-1 items-center gap-3 overflow-hidden sm:gap-4">
      {/* Show + track info — click to expand into the now-playing sheet */}
      <div
        onClick={() => {
          if (showLoaded) setExpanded(true);
        }}
        className={`flex min-w-0 flex-1 items-center gap-3 ${
          showLoaded ? "cursor-pointer" : ""
        }`}
      >
        {showLoaded && (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={artSrc}
            alt=""
            className={`h-10 w-10 flex-shrink-0 rounded bg-white/5 ${
              artIsLogo ? "object-contain p-1" : "object-cover"
            }`}
            referrerPolicy="no-referrer"
          />
        )}
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

      {/* Clear/close — visible whenever a show is loaded (active, parked, or
          remote). Without this, a stuck show occupies the header forever and
          there's no escape from the player state. */}
      {(isActive || isParked || isRemoteActive) && (
        <button
          onClick={dismiss}
          className="flex-shrink-0 rounded-full p-1.5 text-white/40 transition-colors hover:text-white/70"
          aria-label="Clear player"
          title="Clear player"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
            <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
          </svg>
        </button>
      )}

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

      {/* Expand into the full now-playing sheet */}
      {(isActive || isParked || isRemoteActive) && (
        <button
          onClick={() => setExpanded(true)}
          className="flex-shrink-0 rounded-full p-1.5 text-white/40 transition-colors hover:text-white/70"
          aria-label="Expand player"
          title="Expand player"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <path d="M7 14l5-5 5 5z" />
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
          {devicePickerOpen && !expanded && (
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

      {/* ── "Now playing" sheet — full-screen on mobile, a docked landscape
          panel on desktop. Slides up on interaction, down to collapse. ── */}
      <div
        className={`fixed z-[60] flex flex-col bg-gradient-to-b from-deadly-surface to-deadly-bg text-white transition-transform duration-300 ease-out inset-0 lg:inset-x-0 lg:bottom-0 lg:top-auto lg:h-[460px] lg:rounded-t-2xl lg:border-t lg:border-white/10 lg:shadow-2xl lg:shadow-black/50 ${
          expanded ? "translate-y-0" : "pointer-events-none translate-y-full"
        }`}
        aria-hidden={!expanded}
      >
        {/* top bar: collapse handle + devices */}
        <div className="flex items-center justify-between px-4 py-3">
          <button
            onClick={() => setExpanded(false)}
            aria-label="Collapse player"
            className="rounded-full p-2 text-white/60 transition-colors hover:text-white"
          >
            <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
              <path d="M7 10l5 5 5-5z" />
            </svg>
          </button>
          <span className="text-xs font-semibold uppercase tracking-wider text-white/50">
            Now Playing
          </span>
          <div className="relative">
            {isConnected ? (
              <button
                onClick={() => setDevicePickerOpen((o) => !o)}
                aria-label="Connect to device"
                className={`rounded-full p-2 transition-colors ${
                  isRemoteActive ? "text-deadly-highlight" : "text-white/60 hover:text-white"
                }`}
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M1 18v3h4v-3H1zm0-6v3h8v-3H1zm0-6v3h12V6H1zm20 12.59L17.42 15 16 16.41 21 21.41l5-5L24.59 15 21 18.59z" />
                </svg>
              </button>
            ) : (
              <span className="block w-9" />
            )}
            {devicePickerOpen && expanded && (
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
        </div>

        <div className="flex flex-1 flex-col overflow-y-auto lg:flex-row lg:gap-6 lg:overflow-hidden lg:px-6 lg:pb-6">
          {/* media column: artwork + info + seek + transport + recordings */}
          <div className="flex flex-col items-center px-5 pb-6 lg:w-[300px] lg:flex-shrink-0 lg:items-start lg:overflow-y-auto lg:px-0 lg:pb-0">
            {/* artwork (real cover, logo fallback) */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={artSrc}
              alt=""
              referrerPolicy="no-referrer"
              className={`mt-2 aspect-square w-full max-w-[18rem] rounded-lg bg-white/5 shadow-2xl lg:mt-0 lg:max-w-[240px] ${
                artIsLogo ? "object-contain p-6 lg:p-4" : "object-cover"
              }`}
            />

            {/* show + track */}
            <div className="mt-5 w-full text-center lg:text-left">
              <p className="text-xl font-bold text-white">
                {displayTrackTitle ?? showInfo?.date ?? "--"}
              </p>
              {subtitleLine && (
                <p
                  className={`mt-1 text-sm ${
                    isRemoteActive ? "text-deadly-highlight" : "text-white/50"
                  }`}
                >
                  {subtitleLine}
                </p>
              )}
              {displayTrackCount > 1 && (
                <p className="mt-1 text-xs tabular-nums text-white/30">
                  Track {displayTrackIndex + 1} of {displayTrackCount}
                </p>
              )}
            </div>

            {/* seek */}
            <div className="mt-5 w-full">
              <div
                className="h-1.5 w-full cursor-pointer rounded-full bg-white/10"
                onClick={handleSeek}
              >
                <div
                  className="h-full rounded-full bg-deadly-highlight"
                  style={{ width: `${Math.min(progress, 100)}%` }}
                />
              </div>
              <div className="mt-1 flex justify-between text-[11px] tabular-nums text-white/40">
                <span>{formatTime(displayElapsed)}</span>
                <span>{formatTime(displayDuration)}</span>
              </div>
            </div>

            {/* transport */}
            <div className="mt-4 flex w-full items-center justify-center gap-8">
              <button
                onClick={handlePrev}
                disabled={!handlePrev}
                className="text-white/70 transition-colors hover:text-white disabled:text-white/20"
                aria-label="Previous track"
              >
                <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M6 6h2v12H6zm3.5 6 8.5 6V6z" />
                </svg>
              </button>
              <button
                onClick={handleTogglePlayPause}
                disabled={isLoadingTracks || !!(isActive && isActiveDevice && isLoading)}
                className="flex h-16 w-16 items-center justify-center rounded-full bg-white text-deadly-bg transition hover:scale-105 disabled:opacity-50"
                aria-label={displayIsPlaying ? "Pause" : "Play"}
              >
                {isLoadingTracks || (isActive && isActiveDevice && isLoading) ? (
                  <svg width="26" height="26" viewBox="0 0 24 24" fill="currentColor" className="animate-spin">
                    <path d="M12 2a10 10 0 0 1 10 10h-2a8 8 0 0 0-8-8V2z" />
                  </svg>
                ) : displayIsPlaying ? (
                  <svg width="26" height="26" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M6 19h4V5H6zm8-14v14h4V5z" />
                  </svg>
                ) : (
                  <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M8 5v14l11-7z" />
                  </svg>
                )}
              </button>
              <button
                onClick={handleNext}
                disabled={!handleNext}
                className="text-white/70 transition-colors hover:text-white disabled:text-white/20"
                aria-label="Next track"
              >
                <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z" />
                </svg>
              </button>
            </div>

            {/* recording switcher */}
            {activeShow && activeShow.recordings.length > 1 && (
              <div className="w-full">
                <RecordingSelector
                  recordings={activeShow.recordings}
                  selectedId={selectedRecording}
                  onSelect={selectRecording}
                />
              </div>
            )}
          </div>

          {/* tracks column */}
          <div className="px-5 pb-8 lg:flex-1 lg:overflow-y-auto lg:border-l lg:border-white/10 lg:px-6 lg:pb-0">
            {isActive ? (
              <>
                <h4 className="mb-2 mt-4 text-sm font-bold text-deadly-title lg:mt-0">
                  Tracks
                </h4>
                <TrackList
                  tracks={tracks}
                  isLoading={isLoadingTracks}
                  currentTrackIndex={currentTrackIndex}
                  status={status}
                  onPlayTrack={playTrack}
                />
              </>
            ) : (
              <p className="mt-4 hidden text-sm text-white/30 lg:block">
                Press play to load the track list.
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
