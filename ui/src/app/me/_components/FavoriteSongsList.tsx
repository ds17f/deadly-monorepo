"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { fetchFavoriteSongs } from "@/lib/userDataApi";
import { useUserDataRefresh } from "@/lib/userDataEvents";
import type { FavoriteTrack } from "@/types/userdata";
import { useUserData } from "@/contexts/UserDataContext";
import { usePlayer } from "@/contexts/PlayerContext";
import type { ViewedShow } from "@/contexts/PlayerContext";
import ShowArtwork from "@/components/show/ShowArtwork";
import { formatShowDate, formatLocation } from "@/components/show/showFormat";

// Favorite songs as ticket-artwork cards, matching the show cards on the other
// /me tabs but with the track title as the headline. Reads the enriched
// GET /api/user/favorites/songs (date/venue/city/image merged from the
// catalog). Each row plays the show and jumps to the track (click anywhere on
// the card or the Play icon); a Go-to-show icon links to the show page; the
// heart removes it. Remove routes through UserDataContext so the player hearts
// stay in sync.
export default function FavoriteSongsList() {
  const { toggleFavoriteTrack } = useUserData();
  const { playShowTrack } = usePlayer();
  const router = useRouter();
  const [state, setState] = useState<"loading" | "error" | "ready">("loading");
  const [songs, setSongs] = useState<FavoriteTrack[]>([]);

  const load = useCallback(() => {
    fetchFavoriteSongs()
      .then((s) => {
        setSongs(s);
        setState("ready");
      })
      .catch(() => setState((prev) => (prev === "ready" ? "ready" : "error")));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useUserDataRefresh(load);

  function remove(track: FavoriteTrack) {
    setSongs((prev) =>
      prev.filter(
        (t) => !(t.showId === track.showId && t.trackTitle === track.trackTitle),
      ),
    );
    toggleFavoriteTrack(track);
  }

  function play(track: FavoriteTrack) {
    const show: ViewedShow = {
      showId: track.showId,
      recordings: [],
      bestRecordingId: track.recordingId ?? track.bestRecordingId ?? null,
      date: track.date ?? "",
      venue: track.venue ?? "",
      location: formatLocation(track) ?? "",
      image: track.image ?? null,
    };
    playShowTrack(show, track.trackTitle, track.trackNumber);
  }

  const rows = useMemo(
    () =>
      songs.map((s) => ({
        ...s,
        dateLabel: formatShowDate(s),
        location: formatLocation(s),
      })),
    [songs],
  );

  if (state === "loading") {
    return (
      <ul className="space-y-2.5">
        {Array.from({ length: 8 }).map((_, i) => (
          <li
            key={i}
            className="h-[76px] animate-pulse rounded-xl bg-deadly-surface"
          />
        ))}
      </ul>
    );
  }

  if (state === "error") {
    return (
      <div className="rounded-lg border border-white/10 bg-deadly-surface p-8 text-center">
        <p className="text-sm text-white/50">
          Couldn&apos;t load this list. Try again in a moment.
        </p>
      </div>
    );
  }

  if (rows.length === 0) {
    return (
      <div className="rounded-lg border border-white/10 bg-deadly-surface p-8 text-center">
        <h2 className="text-lg font-medium text-white">Favorite songs</h2>
        <p className="mx-auto mt-2 max-w-md text-sm text-white/50">
          Tap the heart next to any track in the player — on this site or any
          device — and it&apos;ll show up here.
        </p>
      </div>
    );
  }

  return (
    <ul className="space-y-2.5">
      {rows.map((s) => (
        <li
          key={`${s.showId}::${s.trackTitle}`}
          className="flex items-center gap-3 rounded-xl border border-white/10 bg-deadly-surface p-2.5 transition hover:border-white/30 hover:bg-white/5"
        >
          {/* Card body — click plays the show and jumps to this track. */}
          <button
            type="button"
            onClick={() => play(s)}
            className="flex min-w-0 flex-1 items-center gap-3 text-left"
          >
            <ShowArtwork image={s.image} alt={s.trackTitle} />
            <div className="min-w-0 flex-1">
              <p className="truncate font-semibold text-white">{s.trackTitle}</p>
              <p className="truncate text-sm text-white/60">
                {s.location ? `${s.dateLabel} · ${s.location}` : s.dateLabel}
              </p>
              {s.venue && (
                <p className="truncate text-xs text-white/40">{s.venue}</p>
              )}
            </div>
          </button>

          {/* Actions: Play · Go to show · Remove. */}
          <div className="flex flex-shrink-0 items-center gap-1">
            <button
              type="button"
              onClick={() => play(s)}
              aria-label="Play"
              title="Play"
              className="rounded-full p-2 text-white/60 transition hover:bg-white/10 hover:text-white"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M8 5v14l11-7z" />
              </svg>
            </button>
            <button
              type="button"
              onClick={() => router.push(`/shows/${s.showId}`)}
              aria-label="Go to show"
              title="Go to show"
              className="rounded-full p-2 text-white/60 transition hover:bg-white/10 hover:text-white"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M7 17 17 7M9 7h8v8" />
              </svg>
            </button>
            <button
              type="button"
              onClick={() => remove(s)}
              aria-label="Remove from favorite songs"
              title="Remove from favorite songs"
              className="rounded-full p-2 text-deadly-highlight transition hover:bg-white/10"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M12 21s-7.5-4.9-10-9.2C.3 8.3 2 5 5.2 5c1.9 0 3.3 1 3.8 2.4C9.5 6 11 5 12.8 5 16 5 17.7 8.3 16 11.8 13.5 16.1 12 21 12 21z" />
              </svg>
            </button>
          </div>
        </li>
      ))}
    </ul>
  );
}
