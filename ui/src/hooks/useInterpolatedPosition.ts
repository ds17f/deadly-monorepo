import { useState, useEffect, useRef } from "react";
import type { UserPlaybackState } from "@/contexts/ConnectContext";

export function useInterpolatedPosition(
  userState: UserPlaybackState | null,
  isLocalPlayback: boolean,
  localElapsed: number,
): number {
  const [interpolated, setInterpolated] = useState(0);
  const rafRef = useRef<number>(0);

  useEffect(() => {
    if (isLocalPlayback) return;
    if (!userState) { setInterpolated(0); return; }

    if (!userState.isPlaying) {
      setInterpolated(userState.positionMs);
      return;
    }

    // Remote playback: interpolate
    function tick() {
      if (!userState) return;
      const now = Date.now();
      setInterpolated(userState.positionMs + (now - userState.updatedAt));
      rafRef.current = requestAnimationFrame(tick);
    }
    rafRef.current = requestAnimationFrame(tick);

    return () => cancelAnimationFrame(rafRef.current);
  }, [userState, userState?.positionMs, userState?.updatedAt, userState?.isPlaying, isLocalPlayback]);

  if (isLocalPlayback) return localElapsed * 1000;
  return interpolated;
}
