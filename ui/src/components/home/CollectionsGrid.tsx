"use client";

import { useState } from "react";
import type { CollectionSummary } from "@/types/homepage";

const INITIAL_COUNT = 12;

export default function CollectionsGrid({
  collections,
}: {
  collections: CollectionSummary[];
}) {
  const [showAll, setShowAll] = useState(false);
  const visible = showAll ? collections : collections.slice(0, INITIAL_COUNT);

  if (collections.length === 0) return null;
  return (
    <section className="mb-8">
      <h3 className="mb-2 text-sm font-bold uppercase tracking-wider text-deadly-title/80">
        Collections
        <span className="ml-2 inline-block h-px w-16 align-middle bg-white/20" />
      </h3>
      <div className="flex flex-wrap gap-2">
        {visible.map((c) => (
          <span
            key={c.id}
            className="rounded-full bg-deadly-surface px-3 py-1 text-sm text-white/80"
            title={c.description}
          >
            {c.name}
            <span className="ml-1 text-xs text-white/40">{c.total_shows}</span>
          </span>
        ))}
        {collections.length > INITIAL_COUNT && (
          <button
            onClick={() => setShowAll((v) => !v)}
            className="rounded-full bg-deadly-surface px-3 py-1 text-sm text-deadly-heading hover:text-white"
          >
            {showAll ? "Show less" : `+${collections.length - INITIAL_COUNT} more`}
          </button>
        )}
      </div>
    </section>
  );
}
