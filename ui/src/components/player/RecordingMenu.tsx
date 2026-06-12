"use client";

import { useEffect, useRef, useState } from "react";
import type { Recording } from "@/types/recording";
import { sourceColors, sourceLabel } from "@/lib/sourceType";

function SourcePill({ type }: { type: string }) {
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${sourceColors(type)}`}>
      {sourceLabel(type)}
    </span>
  );
}

// Overlapping-layers glyph — "this show has multiple recordings to choose from".
// Deliberately distinct from the queue's list icon.
function LayersIcon({ size = 18 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 2 2 7l10 5 10-5-10-5zm0 7.74L4.74 7 12 4.26 19.26 7 12 9.74zM2 12l10 5 10-5-2.27-1.14L12 14.49 4.27 10.86 2 12zm0 5 10 5 10-5-2.27-1.14L12 19.49 4.27 15.86 2 17z" />
    </svg>
  );
}

interface RecordingMenuProps {
  recordings: Recording[];
  selectedId: string | null;
  onSelect: (identifier: string) => void;
  // "icon" = compact glyph for the player bar / fullscreen controls.
  // "pill" = labeled pill for the show page actions row.
  variant?: "icon" | "pill";
  // Which way the popover opens, relative to the trigger.
  openDirection?: "up" | "down";
  align?: "left" | "right";
  className?: string;
}

// A single entry point to choose a recording, surfaced everywhere playback is:
// the show page, the docked player bar, and the fullscreen player. Reads its
// list + selection from props (PlayerContext for live playback, local pending
// state on the show page) so one component serves them all.
export default function RecordingMenu({
  recordings,
  selectedId,
  onSelect,
  variant = "icon",
  openDirection = "up",
  align = "right",
  className = "",
}: RecordingMenuProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);

  // Close on outside click or Escape while open.
  useEffect(() => {
    if (!open) return;
    function onDown(e: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  if (recordings.length === 0) return null;

  const selected = recordings.find((r) => r.identifier === selectedId) ?? null;
  // With a single recording there's nothing to choose — show the source as a
  // static label so the fullscreen "always a spot" rule still reads cleanly.
  const onlyOne = recordings.length <= 1;

  const popoverPos = [
    openDirection === "up" ? "bottom-full mb-2" : "top-full mt-2",
    align === "right" ? "right-0" : "left-0",
  ].join(" ");

  function handlePick(id: string) {
    onSelect(id);
    setOpen(false);
  }

  return (
    <div ref={rootRef} className={`relative ${className}`} data-no-expand>
      {variant === "pill" ? (
        <button
          onClick={() => !onlyOne && setOpen((o) => !o)}
          disabled={onlyOne}
          aria-haspopup="menu"
          aria-expanded={open}
          aria-label="Choose recording"
          title={onlyOne ? "Only one recording" : "Choose a different recording"}
          className={`inline-flex items-center gap-1.5 rounded-full border py-2.5 pl-3 pr-3.5 text-sm font-semibold transition ${
            onlyOne
              ? "border-white/10 text-white/60"
              : "border-white/15 text-white/80 hover:border-white/30 hover:text-white"
          }`}
        >
          <LayersIcon size={16} />
          {selected ? <SourcePill type={selected.source_type} /> : <span>Choose</span>}
          {selected && selected.rating > 0 && (
            <span className="text-xs text-deadly-star">★ {selected.rating.toFixed(1)}</span>
          )}
          {!onlyOne && (
            <span className={`text-xs text-white/40 transition-transform ${open ? "rotate-180" : ""}`}>▾</span>
          )}
        </button>
      ) : (
        <button
          onClick={() => !onlyOne && setOpen((o) => !o)}
          disabled={onlyOne}
          aria-haspopup="menu"
          aria-expanded={open}
          aria-label="Choose recording"
          title={onlyOne ? "Only one recording" : "Choose a different recording"}
          className={`rounded-full p-2 transition-colors disabled:text-white/20 ${
            open ? "text-deadly-highlight" : "text-white/50 hover:text-white/80"
          }`}
        >
          <LayersIcon />
        </button>
      )}

      {open && (
        <div
          role="menu"
          className={`absolute z-50 ${popoverPos} max-h-[60vh] w-72 max-w-[80vw] overflow-y-auto rounded-lg border border-white/10 bg-deadly-surface p-2 shadow-xl shadow-black/40`}
        >
          <p className="px-2 pb-1.5 pt-1 text-xs font-bold uppercase tracking-wider text-deadly-title/80">
            Recordings ({recordings.length})
          </p>
          <div className="space-y-1">
            {recordings.map((rec) => {
              const isSelected = rec.identifier === selectedId;
              return (
                <button
                  key={rec.identifier}
                  role="menuitemradio"
                  aria-checked={isSelected}
                  onClick={() => handlePick(rec.identifier)}
                  className={`flex w-full items-center gap-2 rounded-md px-2 py-2 text-left transition-colors hover:bg-white/5 ${
                    isSelected ? "border border-deadly-highlight/30 bg-white/5" : "border border-transparent"
                  }`}
                >
                  <SourcePill type={rec.source_type} />
                  {rec.rating > 0 && (
                    <span className="text-xs text-deadly-star">★ {rec.rating.toFixed(1)}</span>
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
        </div>
      )}
    </div>
  );
}
