"use client";
import { useEffect } from "react";
import { usePlayer } from "@/contexts/PlayerContext";

export default function AutoplayPrompt() {
  const { autoplayBlocked, autoplayInfo, retryAutoplay, dismissAutoplay } = usePlayer();

  useEffect(() => {
    if (!autoplayBlocked) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") dismissAutoplay();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [autoplayBlocked, dismissAutoplay]);

  if (!autoplayBlocked) return null;

  const showLabel = autoplayInfo?.showDate
    ? `${autoplayInfo.showDate}${autoplayInfo.venue ? ` — ${autoplayInfo.venue}` : ""}`
    : null;
  const deviceLabel = autoplayInfo?.fromDevice ?? "another device";

  return (
    <div className="absolute inset-0 z-50 flex items-center justify-center gap-3 rounded-lg bg-deadly-bg/80 backdrop-blur-sm">
      <span className="text-sm text-white/70">
        {showLabel
          ? <>{deviceLabel} wants to play <span className="text-white">{showLabel}</span></>
          : <>Playback request from {deviceLabel}</>
        }
      </span>
      <button
        onClick={retryAutoplay}
        className="rounded-full bg-deadly-highlight px-4 py-1.5 text-sm font-medium text-black"
      >
        Play
      </button>
      <button onClick={dismissAutoplay} className="text-white/40 hover:text-white/70">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
          <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
        </svg>
      </button>
    </div>
  );
}
