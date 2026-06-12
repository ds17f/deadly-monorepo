"use client";

import { useState } from "react";
import type { Recording } from "@/types/recording";
import { sourceColors, sourceLabel } from "@/lib/sourceType";

function SourcePill({ type }: { type: string }) {
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${sourceColors(type)}`}>
      {sourceLabel(type)}
    </span>
  );
}

interface RecordingSelectorProps {
  recordings: Recording[];
  selectedId: string | null;
  onSelect: (identifier: string) => void;
}

// A collapsed disclosure by default: the header shows the selected source so
// you know what you're about to play, and "Change" opens the full list. This
// keeps a 25-recording list from dominating the page (especially on mobile)
// while still being one tap to browse.
export default function RecordingSelector({
  recordings,
  selectedId,
  onSelect,
}: RecordingSelectorProps) {
  const [open, setOpen] = useState(false);

  if (recordings.length <= 1) return null;

  const selected = recordings.find((r) => r.identifier === selectedId) ?? null;

  return (
    <div className="mt-4">
      <button
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="flex w-full items-center justify-between gap-2 rounded-lg border border-white/15 bg-deadly-surface px-4 py-3 text-left transition-colors hover:bg-white/5"
      >
        <span className="flex min-w-0 items-center gap-2">
          <span className="text-sm font-bold text-deadly-title">Recordings</span>
          <span className="text-xs text-white/40">({recordings.length})</span>
          {selected && !open && (
            <span className="flex min-w-0 items-center gap-1.5">
              <SourcePill type={selected.source_type} />
              {selected.rating > 0 && (
                <span className="text-xs text-deadly-star">
                  {"★"} {selected.rating.toFixed(1)}
                </span>
              )}
            </span>
          )}
        </span>
        <span className="flex flex-shrink-0 items-center gap-1 text-xs text-white/50">
          {open ? "Hide" : "Change"}
          <span className={`inline-block transition-transform ${open ? "rotate-180" : ""}`}>
            ▾
          </span>
        </span>
      </button>

      {open && (
        <div className="mt-2 space-y-1.5">
          {recordings.map((rec) => {
            const isSelected = rec.identifier === selectedId;
            return (
              <button
                key={rec.identifier}
                onClick={() => onSelect(rec.identifier)}
                className={`flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left transition-colors hover:bg-white/5 ${
                  isSelected
                    ? "border border-deadly-highlight/30 bg-white/5"
                    : "border border-transparent"
                }`}
              >
                <SourcePill type={rec.source_type} />
                {rec.rating > 0 && (
                  <span className="text-xs text-deadly-star">
                    {"★"} {rec.rating.toFixed(1)}
                  </span>
                )}
                {rec.review_count > 0 && (
                  <span className="text-xs text-white/40">({rec.review_count})</span>
                )}
                {rec.runtime && (
                  <span className="ml-auto text-xs text-white/30">{rec.runtime}</span>
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
