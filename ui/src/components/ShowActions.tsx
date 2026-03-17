import Image from "next/image";
import AppStoreBadge from "@/components/AppStoreBadge";
import ShowQRCode from "@/components/ShowQRCode";
import type { Recording } from "@/types/recording";
import type { AiShowReview } from "@/types/show";

const PLAY_STORE_URL =
  "https://play.google.com/store/apps/details?id=com.grateful.deadly";
const GOOGLE_PLAY_BADGE_URL =
  "https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png";

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

interface ShowActionsProps {
  showId: string;
  bestRecordingId: string | null;
  firstRecordingId: string | null;
  recordings: Recording[];
  aiReview: AiShowReview | null;
}

export default function ShowActions({
  showId,
  bestRecordingId,
  firstRecordingId,
  recordings,
  aiReview,
}: ShowActionsProps) {
  const archiveId = bestRecordingId ?? firstRecordingId;

  const bestRec =
    recordings.length > 0
      ? recordings.find((r) => r.identifier === bestRecordingId) ??
        recordings.reduce((a, b) => (b.rating > a.rating ? b : a))
      : null;

  const otherCount = recordings.length > 0 ? recordings.length - 1 : 0;
  const reason = aiReview?.best_recording?.reason;

  return (
    <div id="listen">
      {bestRec && (
        <div className="mb-4 rounded-lg border border-deadly-highlight/20 bg-deadly-surface p-4">
          <h4 className="mb-2 text-sm font-bold text-deadly-title">Best Recording</h4>
          <div className="flex flex-wrap items-center gap-2">
            <SourceBadge type={bestRec.source_type} />
            {bestRec.rating > 0 && (
              <span className="text-sm text-deadly-star">
                {"\u2605"} {bestRec.rating.toFixed(1)}
              </span>
            )}
            {bestRec.review_count > 0 && (
              <span className="text-xs text-white/50">
                {bestRec.review_count} review
                {bestRec.review_count !== 1 ? "s" : ""}
              </span>
            )}
          </div>
          {reason && (
            <p className="mt-2 text-sm leading-relaxed text-white/60">
              &ldquo;{reason}&rdquo;
            </p>
          )}
          {otherCount > 0 && (
            <p className="mt-2 text-xs text-white/40">
              {otherCount} other recording{otherCount !== 1 ? "s" : ""}{" "}
              available
            </p>
          )}
        </div>
      )}

      <a
        href={`deadly://show/${showId}`}
        className="inline-flex w-full items-center justify-center gap-2 rounded-lg border border-deadly-accent px-5 py-3 font-semibold text-white transition-colors hover:bg-deadly-accent/10 lg:hidden"
      >
        <Image src="/logo.png" alt="" width={24} height={24} />
        Listen on Deadly
      </a>

      <ShowQRCode showId={showId} />

      <div className="mt-3 flex items-center gap-3">
        <a
          href={PLAY_STORE_URL}
          target="_blank"
          rel="noopener noreferrer"
        >
          <Image
            src={GOOGLE_PLAY_BADGE_URL}
            alt="Get it on Google Play"
            width={140}
            height={42}
            unoptimized
          />
        </a>
        <AppStoreBadge />
      </div>

      {archiveId && (
        <a
          href={`https://archive.org/details/${archiveId}`}
          target="_blank"
          rel="noopener noreferrer"
          className="mt-3 inline-flex w-full items-center justify-center gap-2 rounded-lg border border-white/20 px-5 py-3 font-semibold text-white/50 transition-colors hover:text-white/70 hover:bg-white/10"
        >
          <Image src="/archive-org-logo.svg" alt="" width={20} height={20} className="invert opacity-50" />
          Listen on Archive.org
        </a>
      )}
    </div>
  );
}
