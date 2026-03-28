"use client";

import { useEffect, useState } from "react";
import {
  fetchShow,
  fetchShowRecordings,
  fetchArtist,
  showRowToShow,
  recordingRowToRecording,
} from "@/lib/artistApi";
import type { Show } from "@/types/show";
import type { Recording } from "@/types/recording";
import ShowHeader from "@/components/ShowHeader";
import ShowActions from "@/components/ShowActions";
import Setlist from "@/components/Setlist";
import ShowPlayerPanel from "@/components/player/ShowPlayerPanel";

export default function DynamicShowPage({ showId }: { showId: string }) {
  const [show, setShow] = useState<Show | null>(null);
  const [recordings, setRecordings] = useState<Recording[]>([]);
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

        // Fetch artist name
        let artistName = "Unknown Artist";
        try {
          const artist = await fetchArtist(showRow.artist_id);
          artistName = artist.name;
        } catch { /* use fallback */ }

        setShow(showRowToShow(showRow, recRows, artistName));
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

  return (
    <article>
      <div className="grid grid-cols-1 gap-x-12 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <ShowHeader show={show} />
          {show.setlist && show.setlist.length > 0 && (
            <Setlist sets={show.setlist} songHighlights={[]} />
          )}
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
        </div>
      </div>
    </article>
  );
}
