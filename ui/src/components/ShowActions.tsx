import Image from "next/image";
import AppStoreBadge from "@/components/AppStoreBadge";
import ListenOnDeadlyLink from "@/components/ListenOnDeadlyLink";

const PLAY_STORE_URL =
  "https://play.google.com/store/apps/details?id=com.grateful.deadly";
// Self-hosted (vendored into /public) — hot-linking from play.google.com
// rendered intermittently (blockers / remote hiccups, no local fallback).
const GOOGLE_PLAY_BADGE_URL = "/google-play-badge.png";

interface ShowActionsProps {
  showId: string;
  bestRecordingId: string | null;
  firstRecordingId: string | null;
}

export default function ShowActions({
  showId,
  bestRecordingId,
  firstRecordingId,
}: ShowActionsProps) {
  const archiveId = bestRecordingId ?? firstRecordingId;

  return (
    <div id="listen">
      <ListenOnDeadlyLink showId={showId} recordingId={archiveId} />

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
