"use client";

import { createContext, useContext } from "react";

export interface ConnectDevice {
  deviceId: string;
  type: "ios" | "android" | "web";
  name: string;
  capabilities: string[];
}

export interface SessionTrack {
  title: string;
  duration: number; // seconds
}

export interface PlaybackState {
  showId: string;
  recordingId: string;
  trackIndex: number;
  positionMs: number;
  durationMs?: number;
  trackTitle?: string;
  status: "playing" | "paused" | "stopped";
  // Show metadata for the receiving device
  date?: string;
  venue?: string;
  location?: string;
  // Track list for server-side navigation
  tracks?: SessionTrack[];
}

export interface ActiveSession {
  deviceId: string;
  deviceName: string;
  deviceType: "ios" | "android" | "web";
  state: PlaybackState;
  updatedAt: number;
}

export interface UserPlaybackState {
  showId: string;
  recordingId: string;
  trackIndex: number;
  positionMs: number;
  durationMs: number;
  trackTitle?: string;
  date?: string;
  venue?: string;
  location?: string;

  // Nullable — null means "parked" (no device actively playing)
  activeDeviceId: string | null;
  activeDeviceName: string | null;
  activeDeviceType: "ios" | "android" | "web" | null;
  isPlaying: boolean;

  // Server-managed track list
  tracks?: SessionTrack[];

  updatedAt: number;
}

export interface ConnectConfig {
  positionUpdateIntervalMs: number;
  seekDivergenceThresholdMs: number;
  redirectMaxAgeSec: number;
  seekSettleDelayMs: number;
}

export const DEFAULT_CONNECT_CONFIG: ConnectConfig = {
  positionUpdateIntervalMs: 5000,
  seekDivergenceThresholdMs: 2000,
  redirectMaxAgeSec: 120,
  seekSettleDelayMs: 500,
};

export interface ConnectContextValue {
  isConnected: boolean;
  devices: ConnectDevice[];
  activeSession: ActiveSession | null;
  userState: UserPlaybackState | null;
  isActiveDevice: boolean;
  connectConfig: ConnectConfig;
  setUserState: React.Dispatch<React.SetStateAction<UserPlaybackState | null>>;

  announcePlayback: (state: PlaybackState) => void;
  claimSession: () => void;
  playOnDevice: (deviceId: string, state: PlaybackState) => void;
  sendPositionUpdate: (state: PlaybackState) => void;
  sendCommand: (action: string, seekMs?: number) => void;
  clearState: () => void;
}

export const ConnectContext = createContext<ConnectContextValue | null>(null);

const DEFAULT_VALUE: ConnectContextValue = {
  isConnected: false,
  devices: [],
  activeSession: null,
  userState: null,
  isActiveDevice: false,
  connectConfig: DEFAULT_CONNECT_CONFIG,
  setUserState: () => {},
  announcePlayback: () => {},
  claimSession: () => {},
  playOnDevice: () => {},
  sendPositionUpdate: () => {},
  sendCommand: () => {},
  clearState: () => {},
};

export function useConnect(): ConnectContextValue {
  const ctx = useContext(ConnectContext);
  return ctx ?? DEFAULT_VALUE;
}
