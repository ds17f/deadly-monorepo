export type DeviceType = "ios" | "android" | "web";

export interface SessionTrack {
  title: string;
  durationMs: number;
}

export interface ConnectState {
  version: number;
  showId: string | null;
  recordingId: string | null;
  tracks: SessionTrack[];
  trackIndex: number;
  positionMs: number;
  positionTs: number;
  durationMs: number;
  playing: boolean;
  activeDeviceId: string | null;
  activeDeviceName: string | null;
  activeDeviceType: DeviceType | null;
  date: string | null;
  venue: string | null;
  location: string | null;
}

export interface ConnectDevice {
  deviceId: string;
  userId: string;
  type: DeviceType;
  name: string;
  lastHeartbeat: number;
}

// ── Client -> Server messages ────────────────────────────────────────────────

export interface RegisterMessage {
  type: "register";
  deviceId: string;
  deviceType: DeviceType;
  deviceName: string;
}

export interface HeartbeatMessage {
  type: "heartbeat";
}

export interface CommandMessage {
  type: "command";
  action: string;
  [key: string]: unknown;
}

export type ClientMessage = RegisterMessage | HeartbeatMessage | CommandMessage;

// ── Server -> Client messages ────────────────────────────────────────────────

export interface StateMessage {
  type: "state";
  state: ConnectState;
}

export interface DevicesMessage {
  type: "devices";
  devices: Array<{ deviceId: string; deviceType: DeviceType; deviceName: string }>;
}

export interface ErrorMessage {
  type: "error";
  message: string;
  state: ConnectState;
}

export type ServerMessage = StateMessage | DevicesMessage | ErrorMessage;
