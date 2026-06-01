"use client";

import { useEffect, useState } from "react";
import { fetchReviews } from "@/lib/userDataApi";
import type { ShowReview } from "@/types/userdata";
import ShowRow from "@/components/show/ShowRow";
import { formatShowDate, formatLocation } from "@/components/show/showFormat";

type LoadState =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; reviews: ShowReview[] };

// Newest-edited first.
function sortReviews(reviews: ShowReview[]): ShowReview[] {
  return [...reviews].sort((a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0));
}

function ratingLabel(review: ShowReview): string | null {
  const r = review.overallRating;
  if (r == null) return null;
  return `★ ${Number.isInteger(r) ? r : r.toFixed(1)}`;
}

export default function ReviewsTab() {
  const [state, setState] = useState<LoadState>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    fetchReviews()
      .then((reviews) => {
        if (!cancelled) setState({ status: "ready", reviews: sortReviews(reviews) });
      })
      .catch(() => {
        if (!cancelled) setState({ status: "error" });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (state.status === "loading") {
    return (
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <div
            key={i}
            className="h-[76px] animate-pulse rounded-xl border border-white/10 bg-deadly-surface"
          />
        ))}
      </div>
    );
  }

  if (state.status === "error") {
    return (
      <div className="rounded-lg border border-white/10 bg-deadly-surface p-8 text-center">
        <p className="text-sm text-white/50">
          Couldn&apos;t load your reviews. Try again in a moment.
        </p>
      </div>
    );
  }

  if (state.reviews.length === 0) {
    return (
      <div className="rounded-lg border border-white/10 bg-deadly-surface p-8 text-center">
        <h2 className="text-lg font-medium text-white">Reviews</h2>
        <p className="mx-auto mt-2 max-w-md text-sm text-white/50">
          Rate and review a show — on this site or any device — and it&apos;ll
          show up here.
        </p>
      </div>
    );
  }

  return (
    <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {state.reviews.map((review) => (
        <li key={review.showId} className="flex flex-col gap-1.5">
          <ShowRow
            showId={review.showId}
            image={review.image}
            bestRecordingId={review.bestRecordingId}
            date={formatShowDate(review)}
            location={formatLocation(review)}
            venue={review.venue}
            trailing={ratingLabel(review)}
          />
          {review.notes && (
            <p className="px-1 text-sm text-white/60 line-clamp-3">
              {review.notes}
            </p>
          )}
        </li>
      ))}
    </ul>
  );
}
