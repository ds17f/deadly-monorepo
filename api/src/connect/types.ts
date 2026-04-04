export type DeviceType = "ios" | "android" | "web";
export type DeviceCapability = "playback" | "control";

export interface ConnectDevice {
  deviceId: string;
  userId: string;
  type: DeviceType;
  name: string;
  capabilities: DeviceCapability[];
}

export type PlaybackAction = "play" | "pause" | "stop" | "next" | "prev" | "seek";

export interface PlaybackCommand {
  action: PlaybackAction;
  seekMs?: number;
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
  // Show metadata for display on receiving devices
  date?: string;
  venue?: string;
  location?: string;
  // Track list for server-side navigation
  tracks?: SessionTrack[];
}

export interface ActiveSession {
  deviceId: string;
  deviceName: string;
  deviceType: DeviceType;
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
  activeDeviceType: DeviceType | null;
  isPlaying: boolean;

  // Server-managed track list for next/prev resolution
  tracks?: SessionTrack[];

  updatedAt: number;
}

// ── Server Config ───────────────────────────────────────────────────

export interface ConnectConfig {
  positionUpdateIntervalMs: number;
  seekDivergenceThresholdMs: number;
  redirectMaxAgeSec: number;
  seekSettleDelayMs: number;
}

export const DEFAULT_CONFIG: ConnectConfig = {
  positionUpdateIntervalMs: 5000,
  seekDivergenceThresholdMs: 2000,
  redirectMaxAgeSec: 120,
  seekSettleDelayMs: 500,
};

// ── WebSocket Messages ──────────────────────────────────────────────

export interface ConfigMessage {
  type: "config";
  config: ConnectConfig;
}

export interface RegisterMessage {
  type: "register";
  device: Omit<ConnectDevice, "userId">;
}

export interface DevicesMessage {
  type: "devices";
  devices: Omit<ConnectDevice, "userId">[];
}

export interface CommandMessage {
  type: "command";
  command: PlaybackCommand;
}

export interface PositionUpdateMessage {
  type: "position_update";
  state: PlaybackState;
}

export interface SessionUpdateMessage {
  type: "session_update";
  state: PlaybackState;
}

export interface SessionClaimMessage {
  type: "session_claim";
}

export interface SessionPlayOnMessage {
  type: "session_play_on";
  targetDeviceId: string;
  state: PlaybackState;
}

export interface SessionPlayOnReceivedMessage {
  type: "session_play_on";
  state: PlaybackState;
  fromDeviceName: string;
  relayedAt: number;
}

export interface ActiveSessionMessage {
  type: "active_session";
  session: ActiveSession | null;
}

export interface UserStateMessage {
  type: "user_state";
  state: UserPlaybackState | null;
}

export interface StateClearMessage {
  type: "state_clear";
}

export interface SessionStopMessage {
  type: "session_stop";
}

export interface ErrorMessage {
  type: "error";
  message: string;
}

export type ConnectMessage =
  | ConfigMessage
  | RegisterMessage
  | DevicesMessage
  | CommandMessage
  | PositionUpdateMessage
  | SessionUpdateMessage
  | SessionClaimMessage
  | SessionPlayOnMessage
  | SessionPlayOnReceivedMessage
  | ActiveSessionMessage
  | UserStateMessage
  | StateClearMessage
  | SessionStopMessage
  | ErrorMessage;
