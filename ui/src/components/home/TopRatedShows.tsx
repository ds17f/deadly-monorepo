"use client";

import type { ShowIndexEntry } from "@/types/homepage";
import ShowCard from "./ShowCard";

export default function TopRatedShows({
  shows,
}: {
  shows: ShowIndexEntry[];
}) {
  if (shows.length === 0) return null;
  return (
    <section className="mb-12">
      <h2 className="mb-4 text-lg font-bold text-deadly-title">
        Top Rated Shows
      </h2>
      <div className="flex snap-x snap-mandatory gap-4 overflow-x-auto pb-4 scrollbar-none">
        {shows.map((show) => (
          <div key={show.id} className="snap-start">
            <ShowCard show={show} />
          </div>
        ))}
      </div>
    </section>
  );
}
