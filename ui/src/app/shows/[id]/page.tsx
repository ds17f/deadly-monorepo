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
import Lineup from "@/components/Lineup";
import ShowReview from "@/components/ShowReview";
import ShowNav from "@/components/ShowNav";
import ShowPlayerPanel from "@/components/player/ShowPlayerPanel";
import FavoriteButton from "@/components/userdata/FavoriteButton";
import UserReview from "@/components/userdata/UserReview";
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

  return (
    <article>
      <ShowNav prevId={prev} nextId={next} />
      <div className="grid grid-cols-1 gap-x-12 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <div className="flex items-start justify-between gap-4">
            <ShowHeader show={show} />
            <FavoriteButton showId={show.show_id} />
          </div>
          {show.setlist && show.setlist.length > 0 && (
            <Setlist sets={show.setlist} songHighlights={songHighlights} />
          )}
          {show.ai_show_review && <ShowReview review={show.ai_show_review} />}
          <UserReview showId={show.show_id} />
        </div>
        <div className="mt-6 lg:mt-0">
          <ShowActions
            showId={show.show_id}
            bestRecordingId={show.best_recording}
            firstRecordingId={show.recordings[0] ?? null}
            recordings={recordings}
            aiReview={show.ai_show_review}
          />
          <ShowPlayerPanel
            recordings={recordings}
            bestRecordingId={show.best_recording}
            showId={show.show_id}
            date={show.date}
            venue={show.venue}
            location={show.location_raw}
          />
          {show.lineup && show.lineup.length > 0 && (
            <Lineup members={show.lineup} />
          )}
        </div>
      </div>
    </article>
  );
}
