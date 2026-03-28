"use client";

import { useEffect, useState } from "react";
import type { Artist } from "@/types/artist";
import { fetchArtists } from "@/lib/artistApi";
import HeroSection from "@/components/home/HeroSection";
import ArtistGrid from "@/components/home/ArtistGrid";
import GetTheApp from "@/components/home/GetTheApp";

export default function Home() {
  const [artists, setArtists] = useState<Artist[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchArtists()
      .then(setArtists)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  const totalShows = artists.reduce((sum, a) => sum + a.show_count, 0);
  const totalRecordings = artists.reduce((sum, a) => sum + a.recording_count, 0);

  return (
    <div className="grid grid-cols-1 gap-x-12 lg:grid-cols-3">
      <div className="lg:col-span-2">
        <HeroSection totalShows={totalShows} totalRecordings={totalRecordings} />
        {loading && (
          <p className="text-sm text-white/50">Loading artists...</p>
        )}
        {error && (
          <p className="text-sm text-red-400">Failed to load artists: {error}</p>
        )}
        {!loading && !error && <ArtistGrid artists={artists} />}
      </div>
      <div className="mt-6 lg:mt-0">
        <GetTheApp />
      </div>
    </div>
  );
}
