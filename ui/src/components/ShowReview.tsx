"use client";

import { useState } from "react";
import type { AiShowReview } from "@/types/show";

// The AI review prose, shown near the top of the show's main column as
// "About this show". The short blurb is always visible; the long-form review
// body is collapsed behind a "Show more" toggle so it doesn't dominate the
// page (especially on mobile). The structured parts (key highlights,
// must-listen sequences, band performance, best recording) live in the
// liner-notes right rail — see ShowLinerNotes.
export default function ShowReview({ review }: { review: AiShowReview }) {
  const [expanded, setExpanded] = useState(false);

  if (!review.blurb && !review.review) return null;

  return (
    <section className="mb-8">
      <h3 className="mb-3 text-lg font-bold text-deadly-title">About this show</h3>

      {review.blurb && (
        <p className="leading-relaxed text-white/80">{review.blurb}</p>
      )}

      {review.review && (
        <>
          {expanded && (
            <p className="mt-3 text-sm leading-relaxed text-white/60">
              {review.review}
            </p>
          )}
          <button
            onClick={() => setExpanded((e) => !e)}
            className="mt-2 text-sm font-semibold text-deadly-blue hover:underline"
          >
            {expanded ? "Show less" : "Show more"}
          </button>
        </>
      )}
    </section>
  );
}
