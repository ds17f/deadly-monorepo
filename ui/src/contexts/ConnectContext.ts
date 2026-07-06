"use client";

import { createContext, useContext } from "react";
import type { ConnectDevice, ConnectState } from "@/types/connect";

// ADR-0011 Chunk B: a snapshot of this device's LOCAL playback, read by the
// Connect heartbeat to renew the ownership lease. Returned by the source the
// player registers via setLocalPlaybackSource; null when nothing is loaded.
export interface LocalPlaybackSnapshot {
  playing: boolean;
  recordingId: string;
  // ADR-0011 §2: the lease heals an ownerless session to the device's REAL
  // track; without this the server heals to track 0 and snaps the still-playing
  // device back on a restart-reclaim.
  trackIndex: number;
  positionMs: number;
}

// ADR-0016 §3 full-state handshake source. A richer snapshot than the heartbeat
// lease — it carries the track list + show metadata so the server can adopt this
// tab's live audio as authoritative on (re)connect, making its FIRST post-register
// broadcast reflect reality instead of stale server memory. Returned by the source
// the player registers via setLocalHandshakeSource; null unless this tab is the
// active device AND locally playing a recording.
export interface LocalHandshakeSnapshot {
  playing: boolean;
  showId?: string;
  recordingId: string;
  tracks?: { title: string; durationMs: number }[];
  trackIndex: number;
  positionMs: number;
  durationMs?: number;
  date?: string | null;
  venue?: string | null;
  location?: string | null;
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
  // ADR-0016 §3: the player registers a getter for this tab's full-state
  // handshake, read once per (re)connect to build the optional `register`
  // handshake. Pass null to clear.
  setLocalHandshakeSource: (source: (() => LocalHandshakeSnapshot | null) | null) => void;
  // Server-clock - local-clock, in ms. Add to Date.now() to approximate the
  // server's current wall-clock. 0 until the first time_sync round-trip
  // completes. See docs/connect-v2-architecture.md "Clock Sync".
  serverTimeOffsetMs: number;
  // ADR-0018: global server kill switch (greys the Connect UI when off) and the
  // per-install beta opt-in + its setter.
  serverConnectEnabled: boolean;
  // Re-fetch the global kill switch. Called when the Connect UI is opened so the
  // correct mode is shown at that critical moment even if the value went stale.
  refreshServerConnectEnabled: () => void;
  connectOptedIn: boolean;
  setConnectOptedIn: (value: boolean) => void;
}

export const ConnectContext = createContext<ConnectContextValue | null>(null);

export function useConnect(): ConnectContextValue {
  const ctx = useContext(ConnectContext);
  if (!ctx) {
    throw new Error("useConnect must be used within a ConnectProvider");
  }
  return ctx;
}
