"use client";

import { useState, useEffect } from "react";
import { useConnect } from "@/contexts/ConnectContext";
import { usePlayer } from "@/contexts/PlayerContext";

export default function NowPlaying() {
  const { incomingState } = useConnect();
  const { playShow, playTrack, seek } = usePlayer();
  const [accepted, setAccepted] = useState(false);

  useEffect(() => {
    if (!incomingState) {
      setAccepted(false);
      return;
    }
    // Auto-accept transfer: start playing the incoming state
    setAccepted(true);
    // The show page would need to be loaded for full playback;
    // for now we emit a custom event that the player can handle
    window.dispatchEvent(
      new CustomEvent("connect:transfer", { detail: incomingState })
    );
  }, [incomingState]);

  if (!incomingState || !accepted) return null;

  const progressPercent = incomingState.positionMs
    ? Math.min(100, (incomingState.positionMs / 1000 / 300) * 100) // rough estimate
    : 0;

  return (
    <div className="fixed bottom-0 left-0 right-0 z-50 border-t border-deadly-highlight/30 bg-deadly-bg/95 px-4 py-3 backdrop-blur">
      <div className="mx-auto flex max-w-5xl items-center gap-4">
        <div className="flex items-center gap-2">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" className="text-deadly-highlight">
            <path d="M8 5v14l11-7z" />
          </svg>
          <span className="text-xs font-medium text-deadly-highlight">
            Playing from another device
          </span>
        </div>
        <div className="flex-1">
          <div className="h-1 rounded-full bg-white/10">
            <div
              className="h-full rounded-full bg-deadly-highlight transition-[width] duration-300"
              style={{ width: `${progressPercent}%` }}
            />
          </div>
        </div>
        <span className="text-xs text-white/40">
          {incomingState.showId}
        </span>
      </div>
    </div>
  );
}
