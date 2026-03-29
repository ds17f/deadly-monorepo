"use client";

import { useEffect, useState } from "react";
import {
  fetchShow,
  fetchShowRecordings,
  fetchArtist,
  fetchShowAdjacent,
  fetchShowReviews,
  showRowToShow,
  recordingRowToRecording,
} from "@/lib/artistApi";
import type { AdjacentShows, ShowReviewRow } from "@/lib/artistApi";
import type { Show, AiShowReview } from "@/types/show";
import type { Recording } from "@/types/recording";
import ShowHeader from "@/components/ShowHeader";
import ShowActions from "@/components/ShowActions";
import Setlist from "@/components/Setlist";
import Lineup from "@/components/Lineup";
import ShowReview from "@/components/ShowReview";
import ShowPlayerPanel from "@/components/player/ShowPlayerPanel";
import DynamicShowNav from "@/components/DynamicShowNav";
import FavoriteButton from "@/components/userdata/FavoriteButton";
import UserReview from "@/components/userdata/UserReview";

export default function DynamicShowPage({ showId, selectedRecordingId }: { showId: string; selectedRecordingId?: string }) {
  const [show, setShow] = useState<Show | null>(null);
  const [recordings, setRecordings] = useState<Recording[]>([]);
  const [adjacent, setAdjacent] = useState<AdjacentShows>({ prev: null, next: null });
  const [reviews, setReviews] = useState<ShowReviewRow[]>([]);
  const [artistId, setArtistId] = useState<string>("");
  const [artistName, setArtistName] = useState<string>("Unknown Artist");
  const [artistImageUrl, setArtistImageUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      try {
        const [showRow, recRows] = await Promise.all([
          fetchShow(showId),
          fetchShowRecordings(showId),
        ]);

        if (!showRow) {
          setError("Show not found");
          return;
        }

        // Fetch artist, adjacent, and reviews in parallel
        const [artistResult, adj, revs] = await Promise.all([
          fetchArtist(showRow.artist_id).catch(() => null),
          fetchShowAdjacent(showId),
          fetchShowReviews(showId),
        ]);

        const name = artistResult?.name ?? "Unknown Artist";
        setArtistId(showRow.artist_id);
        setArtistName(name);
        setArtistImageUrl(artistResult?.image_url ?? null);
        setAdjacent(adj);
        setReviews(revs);

        const mappedShow = showRowToShow(showRow, recRows, name);

        // If we have an AI review from the reviews endpoint, attach it
        const aiReview = revs.find((r) => r.type === "ai");
        if (aiReview?.content) {
          mappedShow.ai_show_review = aiReview.content as unknown as AiShowReview;
        }

        setShow(mappedShow);
        setRecordings(recRows.map(recordingRowToRecording));
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to load show");
      } finally {
        setLoading(false);
      }
    }

    load();
  }, [showId]);

  if (loading) {
    return <p className="text-sm text-white/50">Loading show...</p>;
  }

  if (error || !show) {
    return <p className="text-sm text-red-400">{error ?? "Show not found"}</p>;
  }

  const coverImage = show.cover_image_url ?? artistImageUrl;
  const songHighlights = show.ai_show_review?.song_highlights ?? [];

  return (
    <article>
      <DynamicShowNav adjacent={adjacent} artistId={artistId} artistName={artistName} />
      {coverImage && (
        <div className="mb-6 flex justify-center">
          <img
            src={coverImage}
            alt={`${show.band} ${show.date}`}
            className="max-h-64 rounded-lg object-contain"
          />
        </div>
      )}
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
            artistId={artistId}
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
