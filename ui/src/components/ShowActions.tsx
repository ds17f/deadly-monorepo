import Image from "next/image";
import ListenOnDeadlyLink from "@/components/ListenOnDeadlyLink";

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
