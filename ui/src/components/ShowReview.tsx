import type { AiShowReview } from "@/types/show";

// The long-form AI review prose, shown inline in the show's main column as
// "About this show". The structured parts (key highlights, must-listen
// sequences, band performance, best recording) now live in the liner-notes
// right rail — see ShowLinerNotes. We deliberately render the FULL review
// here (blurb + the long `review` body), not just the blurb.
export default function ShowReview({ review }: { review: AiShowReview }) {
  if (!review.blurb && !review.review) return null;

  return (
    <section className="mb-8">
      <h3 className="mb-4 text-lg font-bold text-deadly-title">About this show</h3>

      {review.blurb && (
        <p className="mb-4 leading-relaxed text-white/80">{review.blurb}</p>
      )}

      {review.review && (
        <p className="text-sm leading-relaxed text-white/60">{review.review}</p>
      )}

      <p className="mt-4 text-[11px] uppercase tracking-wider text-white/30">
        🤖 AI-generated review
        {review.ratings?.confidence
          ? ` · confidence ${review.ratings.confidence}`
          : ""}
      </p>
    </section>
  );
}
