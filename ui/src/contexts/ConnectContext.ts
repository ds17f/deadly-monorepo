"use client";

import { createContext, useContext } from "react";
import type { ConnectDevice, ConnectState } from "@/types/connect";

export interface ConnectContextValue {
  devices: ConnectDevice[];
  state: ConnectState | null;
  myDeviceId: string | null;
  connected: boolean;
  sendCommand: (action: string, extra?: Record<string, unknown>) => void;
}

export const ConnectContext = createContext<ConnectContextValue | null>(null);

export function useConnect(): ConnectContextValue {
  const ctx = useContext(ConnectContext);
  if (!ctx) {
    throw new Error("useConnect must be used within a ConnectProvider");
  }
  return ctx;
}
