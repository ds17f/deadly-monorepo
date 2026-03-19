"use client";

import { useState, useEffect } from "react";
import { useUserData } from "@/contexts/UserDataContext";
import type { ShowReview } from "@/types/userdata";

export default function UserReview({ showId }: { showId: string }) {
  const { getReview, saveReview, removeReview } = useUserData();
  const existing = getReview(showId);

  const [isEditing, setIsEditing] = useState(false);
  const [notes, setNotes] = useState(existing?.notes ?? "");
  const [overallRating, setOverallRating] = useState(existing?.overallRating ?? 0);
  const [recordingQuality, setRecordingQuality] = useState(existing?.recordingQuality ?? 0);
  const [playingQuality, setPlayingQuality] = useState(existing?.playingQuality ?? 0);

  useEffect(() => {
    setNotes(existing?.notes ?? "");
    setOverallRating(existing?.overallRating ?? 0);
    setRecordingQuality(existing?.recordingQuality ?? 0);
    setPlayingQuality(existing?.playingQuality ?? 0);
  }, [existing]);

  function handleSave() {
    const review: ShowReview = {
      showId,
      notes: notes.trim() || null,
      overallRating: overallRating || null,
      recordingQuality: recordingQuality || null,
      playingQuality: playingQuality || null,
    };
    saveReview(review);
    setIsEditing(false);
  }

  function handleDelete() {
    removeReview(showId);
    setIsEditing(false);
    setNotes("");
    setOverallRating(0);
    setRecordingQuality(0);
    setPlayingQuality(0);
  }

  // Show existing review in read mode
  if (existing && !isEditing) {
    return (
      <div className="mt-6 rounded-lg border border-white/10 p-4">
        <div className="mb-2 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-white/70">Your Review</h3>
          <button
            onClick={() => setIsEditing(true)}
            className="text-xs text-deadly-highlight hover:underline"
          >
            Edit
          </button>
        </div>
        {existing.overallRating ? (
          <div className="mb-2">
            <StarDisplay rating={existing.overallRating} />
          </div>
        ) : null}
        {existing.notes && (
          <p className="text-sm text-white/60">{existing.notes}</p>
        )}
        {(existing.recordingQuality || existing.playingQuality) && (
          <div className="mt-2 flex gap-4 text-xs text-white/40">
            {existing.recordingQuality ? <span>Recording: {existing.recordingQuality}/5</span> : null}
            {existing.playingQuality ? <span>Playing: {existing.playingQuality}/5</span> : null}
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="mt-6 rounded-lg border border-white/10 p-4">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-white/70">
          {existing ? "Edit Review" : "Add Your Review"}
        </h3>
        {!isEditing && !existing && (
          <button
            onClick={() => setIsEditing(true)}
            className="text-xs text-deadly-highlight hover:underline"
          >
            Write a review
          </button>
        )}
      </div>

      {!isEditing && !existing ? null : (
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
              onClick={() => setIsEditing(false)}
              className="rounded px-4 py-1.5 text-sm text-white/40 hover:text-white/60"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function StarInput({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  return (
    <div className="flex gap-0.5">
      {[1, 2, 3, 4, 5].map((star) => (
        <button
          key={star}
          onClick={() => onChange(star === value ? 0 : star)}
          className="p-0.5"
        >
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

function StarDisplay({ rating }: { rating: number }) {
  return (
    <div className="flex gap-0.5">
      {[1, 2, 3, 4, 5].map((star) => (
        <svg
          key={star}
          width="14"
          height="14"
          viewBox="0 0 24 24"
          fill={star <= rating ? "currentColor" : "none"}
          stroke="currentColor"
          strokeWidth="1.5"
          className={star <= rating ? "text-yellow-400" : "text-white/20"}
        >
          <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
        </svg>
      ))}
    </div>
  );
}
