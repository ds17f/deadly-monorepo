import Link from "next/link";
import ShowArtwork from "./ShowArtwork";

// One library card, modeled on the mobile show cards: square ticket artwork
// on the left, then date (primary) · city (secondary) · venue (tertiary) —
// date and city are emphasized over the venue name. Optional pin marker and
// a small trailing label (e.g. play count). Links to the show page.
export default function ShowRow({
  showId,
  image,
  bestRecordingId,
  date,
  location,
  venue,
  pinned = false,
  trailing,
}: {
  showId: string;
  image?: string | null;
  bestRecordingId?: string | null;
  date: string;
  location?: string | null;
  venue?: string | null;
  pinned?: boolean;
  trailing?: string | null;
}) {
  return (
    <Link
      href={`/shows/${showId}`}
      className="flex items-center gap-3 rounded-xl border border-white/10 bg-deadly-surface p-2.5 transition hover:border-white/30 hover:bg-white/5"
    >
      <ShowArtwork image={image} bestRecordingId={bestRecordingId} alt={date} />
      <div className="min-w-0 flex-1">
        <p className="flex items-center gap-1 truncate font-semibold text-white">
          {pinned && (
            <svg
              width="12"
              height="12"
              viewBox="0 0 24 24"
              fill="currentColor"
              className="flex-shrink-0 text-deadly-accent"
              aria-label="Pinned"
            >
              <path d="M16 3v2l-1 1v5l3 3v2h-5v6h-2v-6H4v-2l3-3V6L6 5V3z" />
            </svg>
          )}
          <span className="truncate">{date}</span>
        </p>
        {location && (
          <p className="truncate text-sm text-white/60">{location}</p>
        )}
        {venue && <p className="truncate text-xs text-white/40">{venue}</p>}
      </div>
      {trailing && (
        <span className="flex-shrink-0 self-start text-xs text-white/40">
          {trailing}
        </span>
      )}
    </Link>
  );
}
