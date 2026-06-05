"use client";

import { createContext, useContext } from "react";
import type { ConnectDevice, ConnectState } from "@/types/connect";

export interface ConnectContextValue {
  devices: ConnectDevice[];
  state: ConnectState | null;
  myDeviceId: string | null;
  connected: boolean;
  sendCommand: (action: string, extra?: Record<string, unknown>) => void;
  activeDeviceVolume: number | null;
  onVolumeMessage: (handler: (volume: number) => void) => () => void;
  reportVolume: (volume: number) => void;
  // Server-clock - local-clock, in ms. Add to Date.now() to approximate the
  // server's current wall-clock. 0 until the first time_sync round-trip
  // completes. See docs/connect-v2-architecture.md "Clock Sync".
  serverTimeOffsetMs: number;
}

export const ConnectContext = createContext<ConnectContextValue | null>(null);

export function useConnect(): ConnectContextValue {
  const ctx = useContext(ConnectContext);
  if (!ctx) {
    throw new Error("useConnect must be used within a ConnectProvider");
  }
  return ctx;
}
