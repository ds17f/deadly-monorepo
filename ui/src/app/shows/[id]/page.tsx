import type { Metadata } from "next";
import {
  getBuildShowIds,
  getShowById,
  getRecordingById,
  getAdjacentShows,
} from "@/lib/shows";
import ShowHeader from "@/components/ShowHeader";
import ShowActions from "@/components/ShowActions";
import Setlist from "@/components/Setlist";
import ShowReview from "@/components/ShowReview";
import ShowLinerNotes from "@/components/show/ShowLinerNotes";
import ShowAppCta from "@/components/show/ShowAppCta";
import HeroActions from "@/components/show/HeroActions";
import { RightRailSlot } from "@/components/shell/RightRail";
import ShowNav from "@/components/ShowNav";
import type { Recording } from "@/types/recording";
import type { Show } from "@/types/show";

const SITE_URL = (process.env.SITE_URL || "https://thedeadly.app").replace(
  /\/$/,
  "",
);

function resolveCoverImageUrl(show: Show): string {
  const front = show.ticket_images?.find((t) => t.side === "front");
  if (front) return front.url;
  const unknown = show.ticket_images?.find((t) => t.side === "unknown");
  if (unknown) return unknown.url;
  if (show.photos?.length > 0) return show.photos[0].url;
  return `${SITE_URL}/logo.png`;
}

function formatDateForTitle(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString("en-US", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

export async function generateStaticParams() {
  return getBuildShowIds().map((id) => ({ id }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ id: string }>;
}): Promise<Metadata> {
  const { id } = await params;
  const show = getShowById(id);
  const dateStr = formatDateForTitle(show.date);
  const title = `Grateful Dead ${dateStr} — ${show.venue} | The Deadly`;

  const descParts = [`Grateful Dead at ${show.venue}, ${show.location_raw}`];
  const sourceLabel: Record<string, string> = {
    SBD: "Soundboard",
    MATRIX: "Matrix",
    AUD: "Audience",
    FM: "FM broadcast",
  };
  const bestSource = ["SBD", "MATRIX", "FM", "AUD"].find(
    (t) => (show.source_types?.[t] ?? 0) > 0,
  );
  if (bestSource) {
    descParts.push(`${sourceLabel[bestSource]} available`);
  } else if (show.recording_count > 0) {
    descParts.push(`${show.recording_count} recordings`);
  }
  if (show.avg_rating > 0) {
    descParts.push(`${show.avg_rating.toFixed(1)}\u2605`);
  }
  if (show.ai_show_review?.summary) {
    descParts.push(show.ai_show_review.summary);
  }
  const description = descParts.join(" \u2014 ");
  const imageUrl = resolveCoverImageUrl(show);

  return {
    title,
    description,
    openGraph: {
      title,
      description,
      type: "article",
      siteName: "The Deadly",
      images: [{ url: imageUrl }],
    },
    twitter: {
      card: "summary_large_image",
      title,
      description,
      images: [imageUrl],
    },
  };
}

export default async function ShowPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const show = getShowById(id);
  const { prev, next } = getAdjacentShows(id);

  const recordings: Recording[] = show.recordings
    .map((rid) => getRecordingById(rid))
    .filter((r): r is Recording => r !== null);

  const songHighlights = show.ai_show_review?.song_highlights ?? [];

  const coverUrl = resolveCoverImageUrl(show);
  const coverIsLogo = coverUrl.endsWith("/logo.png");
  // Visible cover falls back to the square stealie (the OG/share image keeps
  // the round logo — resolved separately in generateMetadata).
  const heroCover = coverIsLogo ? "/cover-fallback.png" : coverUrl;

  return (
    <article>
      <ShowNav prevId={prev} nextId={next} />

      {/* Album-style hero: cover + identity, with the favorite + play actions
          stacked on the right of the info (centered on mobile). */}
      <div className="mb-6 flex flex-col items-center gap-5 text-center sm:flex-row sm:items-end sm:text-left">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={heroCover}
          alt=""
          className="h-44 w-44 flex-shrink-0 rounded-md bg-white/5 object-cover shadow-2xl sm:h-40 sm:w-40"
          referrerPolicy="no-referrer"
        />
        <div className="flex w-full flex-1 flex-col items-center gap-4 sm:w-auto sm:flex-row sm:items-end sm:justify-between">
          <ShowHeader show={show} />
        </div>
      </div>

      {/* Primary actions on one line: Play · Favorite · Review. Play loads the
          bottom player, which owns the tracklist + recording switching. Order
          below: actions → setlist → about → secondary (get-the-app / archive).
          The liner notes are pushed into the shell's right pane via
          RightRailSlot, so library · content · liner notes are three real
          sibling panes (and the liner notes flow below content on mobile). */}
      <HeroActions
        showId={show.show_id}
        recordings={recordings}
        bestRecordingId={show.best_recording}
        date={show.date}
        venue={show.venue}
        location={show.location_raw}
        image={heroCover}
        review={show.ai_show_review}
      />

      {show.setlist && show.setlist.length > 0 && (
        <div className="mt-6">
          <Setlist sets={show.setlist} songHighlights={songHighlights} />
        </div>
      )}

      {show.ai_show_review && (
        <div className="mt-8">
          <ShowReview review={show.ai_show_review} />
        </div>
      )}

      <div className="mt-8">
        <ShowActions
          showId={show.show_id}
          bestRecordingId={show.best_recording}
          firstRecordingId={show.recordings[0] ?? null}
        />
      </div>

      <RightRailSlot>
        <div className="flex flex-col gap-3">
          <ShowAppCta />
          <ShowLinerNotes
            showId={show.show_id}
            review={show.ai_show_review}
            lineup={show.lineup}
            recordings={recordings}
            bestRecordingId={show.best_recording}
          />
        </div>
      </RightRailSlot>
    </article>
  );
}
