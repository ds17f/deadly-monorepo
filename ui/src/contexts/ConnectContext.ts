"use client";

import { createContext, useContext } from "react";

export interface ConnectDevice {
  deviceId: string;
  type: "ios" | "android" | "web";
  name: string;
  capabilities: string[];
}

export interface PlaybackState {
  showId: string;
  recordingId: string;
  trackIndex: number;
  positionMs: number;
  status: "playing" | "paused" | "stopped";
  // Show metadata for the receiving device
  date?: string;
  venue?: string;
  location?: string;
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
  date?: string;
  venue?: string;
  location?: string;

  // Nullable — null means "parked" (no device actively playing)
  activeDeviceId: string | null;
  activeDeviceName: string | null;
  activeDeviceType: "ios" | "android" | "web" | null;
  isPlaying: boolean;

  updatedAt: number;
}

export interface ConnectContextValue {
  isConnected: boolean;
  devices: ConnectDevice[];
  activeSession: ActiveSession | null;
  userState: UserPlaybackState | null;
  isActiveDevice: boolean;

  // New session-based functions
  announcePlayback: (state: PlaybackState) => void;
  claimSession: () => void;
  playOnDevice: (deviceId: string, state: PlaybackState) => void;
  sendPositionUpdate: (state: PlaybackState) => void;
  clearState: () => void;

  // Legacy — kept for backward compatibility
  incomingState: PlaybackState | null;
  playingOnDevice: ConnectDevice | null;
  transferPlayback: (targetDeviceId: string, state: PlaybackState) => void;
  sendCommand: (targetDeviceId: string, action: string, seekMs?: number) => void;
  clearIncomingState: () => void;
}

export const ConnectContext = createContext<ConnectContextValue | null>(null);

const DEFAULT_VALUE: ConnectContextValue = {
  isConnected: false,
  devices: [],
  activeSession: null,
  userState: null,
  isActiveDevice: false,
  announcePlayback: () => {},
  claimSession: () => {},
  playOnDevice: () => {},
  sendPositionUpdate: () => {},
  clearState: () => {},
  incomingState: null,
  playingOnDevice: null,
  transferPlayback: () => {},
  sendCommand: () => {},
  clearIncomingState: () => {},
};

export function useConnect(): ConnectContextValue {
  const ctx = useContext(ConnectContext);
  return ctx ?? DEFAULT_VALUE;
}
