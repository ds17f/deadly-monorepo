import type { Recording } from "@/types/recording";
import type { AiShowReview } from "@/types/show";

const SOURCE_COLORS: Record<string, string> = {
  SBD: "bg-deadly-highlight text-white",
  FM: "bg-deadly-highlight text-white",
  Matrix: "bg-deadly-highlight text-white",
  Remaster: "bg-deadly-highlight text-white",
  AUD: "bg-amber-700 text-white",
  UNKNOWN: "bg-white/20 text-white/70",
};

function SourceBadge({ type }: { type: string }) {
  const label = type === "UNKNOWN" ? "Unknown" : type;
  const colors = SOURCE_COLORS[type] ?? SOURCE_COLORS.UNKNOWN;
  return (
    <span
      className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${colors}`}
    >
      {label}
    </span>
  );
}

export default function Recordings({
  recordings,
  bestRecording,
  aiReview,
}: {
  recordings: Recording[];
  bestRecording: string | null;
  aiReview: AiShowReview | null;
}) {
  if (recordings.length === 0) return null;

  const best =
    recordings.find((r) => r.identifier === bestRecording) ??
    recordings.reduce((a, b) => (b.rating > a.rating ? b : a));

  const otherCount = recordings.length - 1;
  const reason = aiReview?.best_recording?.reason;

  return (
    <section className="mb-8">
      <h3 className="mb-4 text-lg font-bold text-deadly-title">Best Recording</h3>
      <div className="rounded-lg border border-deadly-highlight/20 bg-deadly-surface p-4">
        <div className="flex flex-wrap items-center gap-2">
          <SourceBadge type={best.source_type} />
          {best.rating > 0 && (
            <span className="text-sm text-deadly-star">
              {"\u2605"} {best.rating.toFixed(1)}
            </span>
          )}
          {best.review_count > 0 && (
            <span className="text-xs text-white/50">
              {best.review_count} review
              {best.review_count !== 1 ? "s" : ""}
            </span>
          )}
        </div>
        {reason && (
          <p className="mt-2 text-sm leading-relaxed text-white/60">
            &ldquo;{reason}&rdquo;
          </p>
        )}
        {otherCount > 0 && (
          <p className="mt-3 text-xs text-white/40">
            {otherCount} other recording{otherCount !== 1 ? "s" : ""} available
          </p>
        )}
      </div>
    </section>
  );
}
