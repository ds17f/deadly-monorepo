"use client";

import { useState } from "react";
import type { CollectionSummary } from "@/types/homepage";
import CollectionCard from "./CollectionCard";

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
    <section className="mb-12">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-bold text-deadly-title">Collections</h2>
        {collections.length > INITIAL_COUNT && (
          <button
            onClick={() => setShowAll((v) => !v)}
            className="text-sm text-deadly-heading hover:text-white"
          >
            {showAll ? "Show less" : `Show all ${collections.length}`}
          </button>
        )}
      </div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {visible.map((c) => (
          <CollectionCard key={c.id} collection={c} />
        ))}
      </div>
    </section>
  );
}
