"use client";

import { usePlayer } from "@/contexts/PlayerContext";
import ShowArtwork from "@/components/show/ShowArtwork";

// ADR-0010 chunk 2: end-of-show "Next up in Ns" announcement. Shows the next
// show's cover + details while the countdown runs, with Play-now / Cancel.
// v1: a docked card above the player bar (active device only). The richer
// "full-screen player previews the next show" treatment layers on from here.
export default function AutoAdvanceBanner() {
  const { autoAdvance, cancelAutoAdvance, playNextNow } = usePlayer();
  if (!autoAdvance) return null;

  const { secondsRemaining, nextShow } = autoAdvance;
  const subtitle = [nextShow.venue, nextShow.location].filter(Boolean).join(" · ");

  return (
    <div className="mx-2 mb-2 flex items-center gap-3 rounded-lg border border-deadly-primary/40 bg-deadly-surface px-3 py-2 shadow-lg">
      {/* Ticket image, else the Deadly logo — same convention as ShowArtwork. */}
      <ShowArtwork image={nextShow.image} alt={nextShow.date} />

      <div className="min-w-0 flex-1">
        <div className="text-xs font-semibold uppercase tracking-wide text-deadly-primary">
          Next up in {secondsRemaining}s
        </div>
        <div className="truncate text-sm font-medium text-white">{nextShow.date}</div>
        {subtitle && <div className="truncate text-xs text-white/60">{subtitle}</div>}
      </div>

      <button
        onClick={playNextNow}
        className="flex-shrink-0 rounded-full bg-deadly-primary px-3 py-1.5 text-xs font-semibold text-black"
      >
        Play now
      </button>
      <button
        onClick={cancelAutoAdvance}
        className="flex-shrink-0 rounded-full border border-white/20 px-3 py-1.5 text-xs font-medium text-white/80"
      >
        Cancel
      </button>
    </div>
  );
}
