"use client";

import Link from "next/link";
import type { Artist } from "@/types/artist";

function formatYears(artist: Artist): string {
  const from = artist.active_from;
  const to = artist.active_to;
  if (!from && !to) return "";
  if (from && !to) return artist.is_active ? `${from}–present` : `${from}`;
  if (from && to) return `${from}–${to}`;
  return "";
}

export default function ArtistGrid({ artists }: { artists: Artist[] }) {
  if (artists.length === 0) {
    return (
      <p className="text-sm text-white/50">No artists available yet.</p>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {artists.map((artist) => (
        <Link
          key={artist.id}
          href={`/artists?id=${artist.id}`}
          className="group rounded-lg bg-deadly-surface p-5 transition-colors hover:bg-white/10"
        >
          {artist.image_url && (
            <div className="mb-3 flex items-center justify-center overflow-hidden rounded-md bg-white/5 p-2">
              <img
                src={artist.image_url}
                alt={artist.name}
                className="max-h-40 w-auto rounded"
              />
            </div>
          )}
          <h3 className="text-lg font-bold text-white group-hover:text-deadly-heading">
            {artist.name}
          </h3>
          {artist.short_name && (
            <span className="text-xs text-white/40">{artist.short_name}</span>
          )}
          <div className="mt-1 text-sm text-white/50">
            {formatYears(artist)}
          </div>
          {artist.description && (
            <p className="mt-2 line-clamp-2 text-sm text-white/40">
              {artist.description}
            </p>
          )}
          <div className="mt-3 flex items-center gap-4 text-xs text-white/50">
            <span>
              {artist.show_count.toLocaleString()} show
              {artist.show_count !== 1 ? "s" : ""}
            </span>
            {artist.recording_count > 0 && (
              <span>
                {artist.recording_count.toLocaleString()} recording
                {artist.recording_count !== 1 ? "s" : ""}
              </span>
            )}
          </div>
        </Link>
      ))}
    </div>
  );
}
