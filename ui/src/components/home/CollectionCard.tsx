"use client";

import type { CollectionSummary } from "@/types/homepage";

export default function CollectionCard({
  collection,
}: {
  collection: CollectionSummary;
}) {
  return (
    <div className="rounded-lg bg-deadly-surface p-4">
      <h3 className="font-medium text-white">{collection.name}</h3>
      {collection.description && (
        <p className="mt-1 line-clamp-2 text-sm text-white/50">
          {collection.description}
        </p>
      )}
      <div className="mt-3 flex flex-wrap gap-1.5">
        {collection.tags.slice(0, 4).map((tag) => (
          <span
            key={tag}
            className="rounded-full bg-white/10 px-2 py-0.5 text-xs text-white/60"
          >
            {tag}
          </span>
        ))}
      </div>
      <div className="mt-2 text-xs text-white/40">
        {collection.total_shows} show{collection.total_shows !== 1 ? "s" : ""}
      </div>
    </div>
  );
}
