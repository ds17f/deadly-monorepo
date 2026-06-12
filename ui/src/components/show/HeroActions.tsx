"use client";

import { useEffect, useState } from "react";
import { useUserData } from "@/contexts/UserDataContext";
import { usePlayer } from "@/contexts/PlayerContext";
import QrButton from "@/components/share/QrButton";
import { useShareLink } from "@/components/share/useShareLink";
import type { Recording } from "@/types/recording";
import type { AiShowReview } from "@/types/show";
import type { ShowReview } from "@/types/userdata";

// The show's primary actions on one line: Play · Favorite · Review · Share —
// each an icon pill. Play loads the bottom player; Favorite toggles; Review
// opens an inline form below the row; QR opens the scan-to-phone code; Share
// copies the public link to the clipboard and flashes a toast. Consolidated so
// they stay on a line while the review form expands.
export default function HeroActions({
  showId,
  recordings,
  bestRecordingId,
  date,
  venue,
  location,
  image,
  review,
}: {
  showId: string;
  recordings: Recording[];
  bestRecordingId: string | null;
  date: string;
  venue: string;
  location: string;
  image?: string | null;
  review?: AiShowReview | null;
}) {
  const player = usePlayer();
  const { isFavorite, toggleFavorite, getReview, saveReview, removeReview } =
    useUserData();

  const fav = isFavorite(showId);
  const existing = getReview(showId);

  const [pendingRecordingId] = useState<string | null>(
    bestRecordingId ?? recordings[0]?.identifier ?? null,
  );

  const [reviewOpen, setReviewOpen] = useState(false);
  const [notes, setNotes] = useState(existing?.notes ?? "");
  const [overallRating, setOverallRating] = useState(existing?.overallRating ?? 0);
  const [recordingQuality, setRecordingQuality] = useState(
    existing?.recordingQuality ?? 0,
  );
  const [playingQuality, setPlayingQuality] = useState(
    existing?.playingQuality ?? 0,
  );

  // Share copies the public link and flashes an app-wide toast.
  const shareLink = useShareLink();

  // Register this show as the viewed show so the player knows what to load.
  useEffect(() => {
    player.setViewedShow({ showId, recordings, bestRecordingId, date, venue, location, image, review });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showId]);

  useEffect(() => {
    setNotes(existing?.notes ?? "");
    setOverallRating(existing?.overallRating ?? 0);
    setRecordingQuality(existing?.recordingQuality ?? 0);
    setPlayingQuality(existing?.playingQuality ?? 0);
  }, [existing]);

  const isActiveShow = player.activeShow?.showId === showId;
  const isPlaying = isActiveShow && player.status === "playing";
  const canPlay = recordings.length > 0;

  function handlePlay() {
    // If this show is already loaded in the player, behave exactly like the
    // player's own play/pause button (toggle). Only fall through to a fresh
    // load when it's a different show or the player is idle/parked.
    if (isActiveShow && player.status !== "idle") {
      player.togglePlayPause();
    } else {
      player.playShow({
        showId,
        recordings,
        bestRecordingId: pendingRecordingId,
        date,
        venue,
        location,
        image,
        review,
      });
    }
  }

  function handleSave() {
    const newReview: ShowReview = {
      showId,
      notes: notes.trim() || null,
      overallRating: overallRating || null,
      recordingQuality: recordingQuality || null,
      playingQuality: playingQuality || null,
    };
    saveReview(newReview);
    setReviewOpen(false);
  }

  function handleDelete() {
    removeReview(showId);
    setReviewOpen(false);
  }

  return (
    <div className="mb-6 w-full">
      <div className="flex flex-wrap items-center justify-center gap-2 sm:justify-start">
        {canPlay && (
          <button
            onClick={handlePlay}
            aria-label={isPlaying ? "Pause" : "Play"}
            className="inline-flex items-center gap-2 rounded-full bg-deadly-accent px-5 py-2.5 text-sm font-bold text-black transition hover:scale-105"
          >
            <Icon name={isPlaying ? "pause" : "play"} />
            {isPlaying ? "Pause" : "Play"}
          </button>
        )}

        <PillButton active={fav} onClick={() => toggleFavorite(showId)}>
          <Icon name="heart" filled={fav} />
          {fav ? "Favorited" : "Favorite"}
        </PillButton>

        <PillButton
          active={player.autoAdvanceEnabled}
          onClick={player.toggleAutoAdvance}
          title="Roll into the next show when this one ends"
        >
          <Icon name="autoplay" />
          Autoplay Next Show
        </PillButton>

        <PillButton active={reviewOpen || !!existing} onClick={() => setReviewOpen((o) => !o)}>
          <Icon name="star" filled={!!existing} />
          {existing ? "Your review" : "Review"}
        </PillButton>

        <QrButton
          showId={showId}
          recordingId={pendingRecordingId}
          subtitle={venue ? `${date} · ${venue}` : date}
        >
          {(open) => (
            <PillButton onClick={open}>
              <Icon name="qr" />
              QR
            </PillButton>
          )}
        </QrButton>

        <PillButton onClick={() => shareLink(showId, pendingRecordingId)}>
          <Icon name="share" />
          Share
        </PillButton>
      </div>

      {reviewOpen && (
        <div className="mt-4 rounded-lg border border-white/10 p-4 text-left">
          <div className="space-y-3">
            <div>
              <label className="mb-1 block text-xs text-white/40">Overall Rating</label>
              <StarInput value={overallRating} onChange={setOverallRating} />
            </div>
            <div>
              <label className="mb-1 block text-xs text-white/40">Notes</label>
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={3}
                className="w-full rounded border border-white/10 bg-white/5 px-3 py-2 text-sm text-white placeholder-white/20 focus:border-deadly-highlight focus:outline-none"
                placeholder="What did you think of this show?"
              />
            </div>
            <div className="flex gap-4">
              <div className="flex-1">
                <label className="mb-1 block text-xs text-white/40">Recording Quality</label>
                <StarInput value={recordingQuality} onChange={setRecordingQuality} />
              </div>
              <div className="flex-1">
                <label className="mb-1 block text-xs text-white/40">Playing Quality</label>
                <StarInput value={playingQuality} onChange={setPlayingQuality} />
              </div>
            </div>
            <div className="flex gap-2">
              <button
                onClick={handleSave}
                className="rounded bg-deadly-highlight px-4 py-1.5 text-sm font-medium text-white transition-opacity hover:opacity-90"
              >
                Save
              </button>
              {existing && (
                <button
                  onClick={handleDelete}
                  className="rounded border border-red-500/30 px-4 py-1.5 text-sm text-red-400 transition-colors hover:bg-red-500/10"
                >
                  Delete
                </button>
              )}
              <button
                onClick={() => setReviewOpen(false)}
                className="rounded px-4 py-1.5 text-sm text-white/40 hover:text-white/60"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function PillButton({
  active,
  onClick,
  title,
  children,
}: {
  active?: boolean;
  onClick: () => void;
  title?: string;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      title={title}
      className={`inline-flex items-center gap-2 rounded-full border px-4 py-2.5 text-sm font-semibold transition hover:scale-105 ${
        active
          ? "border-deadly-accent text-deadly-accent"
          : "border-white/15 text-white/80 hover:border-white/30 hover:text-white"
      }`}
    >
      {children}
    </button>
  );
}

function Icon({ name, filled }: { name: "play" | "pause" | "heart" | "star" | "share" | "qr" | "autoplay"; filled?: boolean }) {
  const common = { width: 18, height: 18, viewBox: "0 0 24 24" };
  if (name === "play") return <svg {...common} fill="currentColor"><path d="M8 5v14l11-7z" /></svg>;
  if (name === "autoplay")
    return (
      <svg {...common} fill="currentColor">
        <path d="M18.6 6.62c-1.44 0-2.8.56-3.77 1.53L7.8 14.39c-.64.64-1.49.99-2.4.99-1.87 0-3.39-1.51-3.39-3.38S3.53 8.62 5.4 8.62c.91 0 1.76.35 2.44 1.03l1.13 1 1.51-1.34L9.22 8.2C8.2 7.18 6.84 6.62 5.4 6.62 2.42 6.62 0 9.04 0 12s2.42 5.38 5.4 5.38c1.44 0 2.8-.56 3.77-1.53l7.03-6.24c.64-.64 1.49-.99 2.4-.99 1.87 0 3.39 1.51 3.39 3.38s-1.52 3.38-3.39 3.38c-.9 0-1.76-.35-2.44-1.03l-1.14-1.01-1.51 1.34 1.27 1.12c1.02 1.01 2.37 1.57 3.82 1.57 2.98 0 5.4-2.41 5.4-5.38s-2.42-5.37-5.4-5.37z" />
      </svg>
    );
  if (name === "pause") return <svg {...common} fill="currentColor"><path d="M6 5h4v14H6zM14 5h4v14h-4z" /></svg>;
  if (name === "share")
    return (
      <svg {...common} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 3v12M8 7l4-4 4 4M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6" />
      </svg>
    );
  if (name === "qr")
    return (
      <svg {...common} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="7" height="7" rx="1" />
        <rect x="14" y="3" width="7" height="7" rx="1" />
        <rect x="3" y="14" width="7" height="7" rx="1" />
        <path d="M14 14h3v3M21 21v.01M21 14v3M14 21h3" />
      </svg>
    );
  if (name === "heart")
    return (
      <svg {...common} fill={filled ? "currentColor" : "none"} stroke="currentColor" strokeWidth="2">
        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
      </svg>
    );
  return (
    <svg {...common} fill={filled ? "currentColor" : "none"} stroke="currentColor" strokeWidth="2">
      <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
    </svg>
  );
}

function StarInput({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  return (
    <div className="flex gap-0.5">
      {[1, 2, 3, 4, 5].map((star) => (
        <button key={star} onClick={() => onChange(star === value ? 0 : star)} className="p-0.5">
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill={star <= value ? "currentColor" : "none"}
            stroke="currentColor"
            strokeWidth="1.5"
            className={star <= value ? "text-yellow-400" : "text-white/20 hover:text-white/40"}
          >
            <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
          </svg>
        </button>
      ))}
    </div>
  );
}
