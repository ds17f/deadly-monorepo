"use client";

import { useState, useRef, useCallback, useEffect } from "react";
import type { Recording } from "@/types/recording";
import type { ArchiveTrack, PlaybackStatus } from "@/types/player";
import { fetchArchiveTracks } from "@/lib/archive";
import PlayerBar from "./PlayerBar";
import TrackList from "./TrackList";
import RecordingSelector from "./RecordingSelector";

const PREV_TRACK_THRESHOLD = 3; // seconds
const AUDIO_RETRY_DELAYS = [0, 1000, 2000];

interface ShowPageClientProps {
  recordings: Recording[];
  bestRecordingId: string | null;
  showId: string;
}

export default function ShowPageClient({
  recordings,
  bestRecordingId,
  showId,
}: ShowPageClientProps) {
  const [selectedRecording, setSelectedRecording] = useState<string | null>(
    bestRecordingId ?? recordings[0]?.identifier ?? null
  );
  const [tracks, setTracks] = useState<ArchiveTrack[] | null>(null);
  const [isLoadingTracks, setIsLoadingTracks] = useState(false);
  const [currentTrackIndex, setCurrentTrackIndex] = useState(-1);
  const [status, setStatus] = useState<PlaybackStatus>("idle");
  const [elapsed, setElapsed] = useState(0);
  const [duration, setDuration] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const retryCountRef = useRef(0);
  const tracksRef = useRef<ArchiveTrack[] | null>(null);

  // Keep tracksRef in sync for use in audio callbacks
  useEffect(() => {
    tracksRef.current = tracks;
  }, [tracks]);

  // Create audio element once
  useEffect(() => {
    const audio = new Audio();
    audio.preload = "auto";
    audioRef.current = audio;

    audio.addEventListener("timeupdate", () => {
      setElapsed(audio.currentTime);
      setDuration(audio.duration || 0);
    });

    audio.addEventListener("playing", () => {
      setStatus("playing");
      retryCountRef.current = 0;
    });

    audio.addEventListener("pause", () => {
      // Only set paused if we didn't trigger a new load
      if (!audio.seeking) {
        setStatus((prev) => (prev === "loading" ? prev : "paused"));
      }
    });

    audio.addEventListener("waiting", () => {
      setStatus("buffering");
    });

    audio.addEventListener("ended", () => {
      // Auto-advance to next track
      setCurrentTrackIndex((prev) => {
        const t = tracksRef.current;
        if (t && prev < t.length - 1) {
          return prev + 1;
        }
        setStatus("paused");
        return prev;
      });
    });

    audio.addEventListener("error", () => {
      const retryIndex = retryCountRef.current;
      if (retryIndex < AUDIO_RETRY_DELAYS.length) {
        const delay = AUDIO_RETRY_DELAYS[retryIndex];
        retryCountRef.current = retryIndex + 1;
        setTimeout(() => {
          audio.load();
          audio.play().catch(() => {});
        }, delay);
      } else {
        setStatus("error");
        setErrorMessage("This track could not be loaded. Try another recording or listen on Archive.org.");
      }
    });

    return () => {
      audio.pause();
      audio.src = "";
    };
  }, []);

  // When currentTrackIndex changes, load and play the track
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !tracks || currentTrackIndex < 0 || currentTrackIndex >= tracks.length) return;

    const track = tracks[currentTrackIndex];
    retryCountRef.current = 0;
    setErrorMessage(null);
    setStatus("loading");
    audio.src = track.url;
    audio.play().catch(() => {
      // Browser may block autoplay — set to paused so user can tap play
      setStatus("paused");
    });

    // Update MediaSession metadata
    updateMediaSession(track);
  }, [currentTrackIndex, tracks]);

  // Register MediaSession action handlers
  useEffect(() => {
    if (!("mediaSession" in navigator)) return;

    navigator.mediaSession.setActionHandler("play", () => togglePlayPause());
    navigator.mediaSession.setActionHandler("pause", () => togglePlayPause());
    navigator.mediaSession.setActionHandler("previoustrack", () => prevTrack());
    navigator.mediaSession.setActionHandler("nexttrack", () => nextTrack());
    navigator.mediaSession.setActionHandler("seekto", (details) => {
      const audio = audioRef.current;
      if (audio && details.seekTime != null) {
        audio.currentTime = details.seekTime;
      }
    });

    return () => {
      navigator.mediaSession.setActionHandler("play", null);
      navigator.mediaSession.setActionHandler("pause", null);
      navigator.mediaSession.setActionHandler("previoustrack", null);
      navigator.mediaSession.setActionHandler("nexttrack", null);
      navigator.mediaSession.setActionHandler("seekto", null);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function updateMediaSession(track: ArchiveTrack) {
    if (!("mediaSession" in navigator)) return;
    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: "Grateful Dead",
      album: showId.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()),
    });
  }

  const playRecording = useCallback(
    async (identifier: string) => {
      setIsLoadingTracks(true);
      setErrorMessage(null);
      try {
        const fetchedTracks = await fetchArchiveTracks(identifier);
        if (fetchedTracks.length === 0) {
          setErrorMessage("No playable audio files found for this recording.");
          setIsLoadingTracks(false);
          return;
        }
        setTracks(fetchedTracks);
        setCurrentTrackIndex(0);
      } catch {
        setErrorMessage("Failed to load tracks from Archive.org.");
      }
      setIsLoadingTracks(false);
    },
    []
  );

  const playTrack = useCallback((index: number) => {
    setCurrentTrackIndex(index);
  }, []);

  const togglePlayPause = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;

    if (status === "idle" && selectedRecording) {
      // First play — fetch tracks and start
      playRecording(selectedRecording);
      return;
    }

    if (audio.paused) {
      audio.play().catch(() => {});
    } else {
      audio.pause();
    }
  }, [status, selectedRecording, playRecording]);

  const nextTrack = useCallback(() => {
    if (!tracks) return;
    setCurrentTrackIndex((prev) => {
      if (prev < tracks.length - 1) return prev + 1;
      return prev;
    });
  }, [tracks]);

  const prevTrack = useCallback(() => {
    const audio = audioRef.current;
    if (!audio || !tracks) return;

    if (audio.currentTime > PREV_TRACK_THRESHOLD || currentTrackIndex === 0) {
      // Restart current track
      audio.currentTime = 0;
    } else {
      setCurrentTrackIndex((prev) => Math.max(0, prev - 1));
    }
  }, [tracks, currentTrackIndex]);

  const seek = useCallback((fraction: number) => {
    const audio = audioRef.current;
    if (!audio || !isFinite(audio.duration)) return;
    audio.currentTime = fraction * audio.duration;
  }, []);

  const handleClose = useCallback(() => {
    const audio = audioRef.current;
    if (audio) {
      audio.pause();
      audio.src = "";
    }
    setStatus("idle");
    setCurrentTrackIndex(-1);
    setTracks(null);
    setElapsed(0);
    setDuration(0);
    setErrorMessage(null);
    if ("mediaSession" in navigator) {
      navigator.mediaSession.metadata = null;
    }
  }, []);

  const handleSelectRecording = useCallback(
    (identifier: string) => {
      setSelectedRecording(identifier);
      // If already playing, switch to the new recording
      if (status !== "idle") {
        playRecording(identifier);
      }
    },
    [status, playRecording]
  );

  if (recordings.length === 0) return null;

  const currentTrack =
    tracks && currentTrackIndex >= 0 ? tracks[currentTrackIndex] : null;
  const hasNext = tracks ? currentTrackIndex < tracks.length - 1 : false;
  const hasPrevious = currentTrackIndex > 0;

  return (
    <>
      {/* Play button — shown when idle */}
      {status === "idle" && selectedRecording && (
        <button
          onClick={() => playRecording(selectedRecording)}
          className="mt-3 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-deadly-highlight px-5 py-3 font-semibold text-white transition-opacity hover:opacity-90"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M8 5v14l11-7z" />
          </svg>
          Play on Web
        </button>
      )}

      {/* Error message */}
      {errorMessage && (
        <div className="mt-3 rounded-lg border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-300">
          <p>{errorMessage}</p>
          {selectedRecording && (
            <a
              href={`https://archive.org/details/${selectedRecording}`}
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
        selectedId={selectedRecording}
        onSelect={handleSelectRecording}
      />

      {/* Track list */}
      <TrackList
        tracks={tracks}
        isLoading={isLoadingTracks}
        currentTrackIndex={currentTrackIndex}
        status={status}
        onPlayTrack={playTrack}
      />

      {/* Fixed bottom player bar */}
      <PlayerBar
        track={currentTrack}
        status={status}
        elapsed={elapsed}
        duration={duration}
        hasNext={hasNext}
        hasPrevious={hasPrevious}
        onTogglePlay={togglePlayPause}
        onNext={nextTrack}
        onPrevious={prevTrack}
        onSeek={seek}
        onClose={handleClose}
      />

      {/* Bottom spacer when player bar is visible */}
      {status !== "idle" && <div className="h-20" />}
    </>
  );
}
