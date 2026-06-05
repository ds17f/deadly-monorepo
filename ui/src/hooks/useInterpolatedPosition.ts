import { useState, useEffect, useRef } from "react";
import type { ConnectState } from "@/types/connect";

// Smoothly-ticking playback position (ms) for displaying a REMOTE device's
// progress. The active device uses its own local audio clock (localElapsedMs);
// everyone else interpolates from the authoritative ConnectState.
//
// Interpolation runs in server-clock: positionTs is the server's wall-clock at
// the last position update, so we translate Date.now() through the measured
// serverTimeOffsetMs before subtracting — a skewed client clock otherwise reads
// the wrong elapsed and the bar jumps on every broadcast. See the Connect v2
// architecture doc, "Clock Sync".
export function useInterpolatedPosition(
  connectState: ConnectState | null,
  serverTimeOffsetMs: number,
  isActiveDevice: boolean,
  localElapsedMs: number,
): number {
  const [interpolated, setInterpolated] = useState(0);
  const rafRef = useRef<number>(0);

  useEffect(() => {
    if (isActiveDevice) return;
    if (!connectState || connectState.showId === null) {
      setInterpolated(0);
      return;
    }

    if (!connectState.playing) {
      setInterpolated(connectState.positionMs);
      return;
    }

    function tick() {
      if (!connectState) return;
      const serverNow = Date.now() + serverTimeOffsetMs;
      setInterpolated(connectState.positionMs + (serverNow - connectState.positionTs));
      rafRef.current = requestAnimationFrame(tick);
    }
    rafRef.current = requestAnimationFrame(tick);

    return () => cancelAnimationFrame(rafRef.current);
  }, [
    connectState,
    connectState?.positionMs,
    connectState?.positionTs,
    connectState?.playing,
    connectState?.showId,
    serverTimeOffsetMs,
    isActiveDevice,
  ]);

  if (isActiveDevice) return localElapsedMs;
  return interpolated;
}
