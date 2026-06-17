"use client";

import { createContext, useContext } from "react";
import type { ConnectDevice, ConnectState } from "@/types/connect";

// ADR-0011 Chunk B: a snapshot of this device's LOCAL playback, read by the
// Connect heartbeat to renew the ownership lease. Returned by the source the
// player registers via setLocalPlaybackSource; null when nothing is loaded.
export interface LocalPlaybackSnapshot {
  playing: boolean;
  recordingId: string;
  positionMs: number;
}

export interface ConnectContextValue {
  devices: ConnectDevice[];
  state: ConnectState | null;
  myDeviceId: string | null;
  connected: boolean;
  sendCommand: (action: string, extra?: Record<string, unknown>) => void;
  activeDeviceVolume: number | null;
  onVolumeMessage: (handler: (volume: number) => void) => () => void;
  reportVolume: (volume: number) => void;
  // ADR-0011 Chunk B: the player registers a getter for this device's local
  // playback so the heartbeat can renew the ownership lease. Pass null to clear.
  setLocalPlaybackSource: (source: (() => LocalPlaybackSnapshot | null) | null) => void;
  // Server-clock - local-clock, in ms. Add to Date.now() to approximate the
  // server's current wall-clock. 0 until the first time_sync round-trip
  // completes. See docs/connect-v2-architecture.md "Clock Sync".
  serverTimeOffsetMs: number;
  // Per-device kill switch for cross-device Connect (Beta). When false, this
  // browser never opens the Connect WebSocket. Persisted to localStorage.
  connectEnabled: boolean;
  setConnectEnabled: (enabled: boolean) => void;
}

export const ConnectContext = createContext<ConnectContextValue | null>(null);

export function useConnect(): ConnectContextValue {
  const ctx = useContext(ConnectContext);
  if (!ctx) {
    throw new Error("useConnect must be used within a ConnectProvider");
  }
  return ctx;
}
