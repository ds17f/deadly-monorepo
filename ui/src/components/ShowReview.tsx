import type { AiShowReview } from "@/types/show";

export default function ShowReview({ review }: { review: AiShowReview }) {
  const highlights = review.key_highlights ?? [];
  const sequences = review.must_listen_sequences ?? [];

  return (
    <section className="mb-8">
      <h3 className="mb-4 text-lg font-bold text-deadly-title">Show Review</h3>

      {review.blurb && (
        <p className="mb-4 text-white/80 leading-relaxed">{review.blurb}</p>
      )}

      {review.review && (
        <p className="mb-6 text-sm text-white/60 leading-relaxed">
          {review.review}
        </p>
      )}

      {highlights.length > 0 && (
        <div className="mb-6">
          <h4 className="mb-2 text-sm font-bold uppercase tracking-wider text-deadly-title/80">
            Key Highlights
            <span className="ml-2 inline-block h-px w-16 align-middle bg-white/20" />
          </h4>
          <ul className="space-y-1 text-sm text-white/70">
            {highlights.map((h) => (
              <li key={h}>
                <span className="mr-2 text-deadly-highlight">&bull;</span>
                {h}
              </li>
            ))}
          </ul>
        </div>
      )}

      {sequences.length > 0 && (
        <div className="mb-6">
          <h4 className="mb-2 text-sm font-bold uppercase tracking-wider text-deadly-title/80">
            Must-Listen Sequences
            <span className="ml-2 inline-block h-px w-16 align-middle bg-white/20" />
          </h4>
          <div className="flex flex-wrap gap-2">
            {sequences.map((seq, i) => (
              <span
                key={i}
                className="rounded-full bg-deadly-surface px-3 py-1 text-sm text-white/80"
              >
                {seq.join(" > ")}
              </span>
            ))}
          </div>
        </div>
      )}

      {review.band_performance && (
        <div className="mb-6">
          <h4 className="mb-2 text-sm font-bold uppercase tracking-wider text-deadly-title/80">
            Band Performance
            <span className="ml-2 inline-block h-px w-16 align-middle bg-white/20" />
          </h4>
          <div className="space-y-2">
            {Object.entries(review.band_performance)
              .filter(([, text]) => text.length > 0)
              .map(([member, text]) => (
                <div key={member}>
                  <span className="font-medium text-white">{member}</span>
                  <span className="text-white/40"> &mdash; </span>
                  <span className="text-sm text-white/60">{text}</span>
                </div>
              ))}
          </div>
        </div>
      )}

    </section>
  );
}
