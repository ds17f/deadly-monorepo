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

  updatedAt: number;
}

// ── WebSocket Messages ──────────────────────────────────────────────

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
  targetDeviceId: string;
  command: PlaybackCommand;
}

export interface CommandReceivedMessage {
  type: "command_received";
  fromDeviceId: string;
  command: PlaybackCommand;
}

export interface TransferMessage {
  type: "transfer";
  targetDeviceId: string;
  state: PlaybackState;
}

export interface TransferReceivedMessage {
  type: "transfer_received";
  fromDeviceId: string;
  state: PlaybackState;
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

export interface ErrorMessage {
  type: "error";
  message: string;
}

export type ConnectMessage =
  | RegisterMessage
  | DevicesMessage
  | CommandMessage
  | CommandReceivedMessage
  | TransferMessage
  | TransferReceivedMessage
  | PositionUpdateMessage
  | SessionUpdateMessage
  | SessionClaimMessage
  | SessionPlayOnMessage
  | SessionPlayOnReceivedMessage
  | ActiveSessionMessage
  | UserStateMessage
  | StateClearMessage
  | ErrorMessage;
