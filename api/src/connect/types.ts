export type DeviceType = "ios" | "android" | "web";

// WS wire-contract version. See docs/PROTOCOL.md for the authoritative semantics.
// Clients that omit `protocolVersion` on register are legacy and treated as 0.
// Behavior is gated on this integer ONLY — never on `appVersion` (ADR-0011 §3).
//
// History (see docs/PROTOCOL.md for the full contract of each):
//   1 — protocolVersion/appVersion on register; heartbeat ownership lease.
//   2 — client understands the 4005 "Connect disabled" close code.
//   3 — full-state handshake on register (ADR-0016 §3) + track-change intent
//       nonce (ADR-0019, generalizing ADR-0017's seekNonce to next/prev).
export const CURRENT_PROTOCOL_VERSION = 3;
export const LEGACY_PROTOCOL_VERSION = 0;

// First protocolVersion that sends a full-state handshake on register and
// honors `trackNonce`. Server branches (handshake adopt, trackNonce bump is
// unconditional but only proto-3 clients react to it) gate on this.
export const HANDSHAKE_PROTOCOL_VERSION = 3;

export interface SessionTrack {
  title: string;
  durationMs: number;
}

export interface ConnectState {
  version: number;
  // Server boot id (ms at process start), constant for the life of the process.
  // Clients use a change in epoch as the explicit, unambiguous signal that the
  // server restarted and rehydrated this session from saved position — which is
  // when a still-playing device should reclaim. A deliberate transition
  // (transfer park, stop) keeps the same epoch, so it's never mistaken for one.
  epoch: number;
  showId: string | null;
  recordingId: string | null;
  tracks: SessionTrack[];
  trackIndex: number;
  positionMs: number;
  positionTs: number;
  durationMs: number;
  // Monotonic counter bumped ONLY by handleSeek (an explicit `seek` command),
  // never by routine `position` reports. The active client honors a remote seek
  // when this advances — keying on intent, not positionMs magnitude. A stale or
  // jittery self-echo of a position report therefore can't trigger a self-seek
  // (the "skips" bug), and a small backward seek from another device is still
  // honored. Additive/optional like pendingAdvance (ADR-0006 §8): old clients
  // ignore it. See ADR-0017.
  seekNonce: number;
  // Monotonic counter bumped ONLY by an explicit `next`/`prev` command
  // (handleNext/handlePrev), never by a `load` (new show) or a routine position
  // report. The active client honors a remote track change when this advances —
  // keying on intent, not on trackIndex coincidence. This is ADR-0017's seekNonce
  // pattern generalized to track changes (ADR-0019): it lets a device suppress the
  // echo of its OWN next/prev (the auto-advance "restart"/spurious-next family)
  // without the fragile "am I already on this index?" guess. Additive/optional:
  // old clients ignore it, new clients coalesce a missing value to 0.
  trackNonce: number;
  playing: boolean;
  activeDeviceId: string | null;
  activeDeviceName: string | null;
  activeDeviceType: DeviceType | null;
  date: string | null;
  venue: string | null;
  location: string | null;

  // ADR-0010 §7: end-of-show countdown shared across the session. Set by the
  // active device's `announce_next`; cleared on load/stop/cancel. `deadline` is
  // an absolute server timestamp (ms) — every device ticks down to it locally,
  // and the active device advances when it's still set and `now >= deadline`.
  // Additive/optional (ADR-0006 §8): older clients ignore it.
  pendingAdvance?: { showId: string; deadline: number } | null;
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
  // ADR-0011 §3. Additive/optional: absent ⇒ legacy ⇒ LEGACY_PROTOCOL_VERSION (0).
  // `protocolVersion` is the wire contract the server may branch on; `appVersion`
  // is build identity for telemetry/"please update" UX only — never branched on.
  protocolVersion?: number;
  appVersion?: string;
  // ADR-0016 §3 full-state handshake (proto >= 3). A device that holds live audio
  // declares its authoritative playback state AS PART OF (re)connecting, so the
  // server's FIRST post-register broadcast already reflects the device's reality
  // instead of the server's stale memory. Reuses the load payload shape; adopted
  // only when the session is ownerless or already this device's (never preempts a
  // different owner) and the device is actually playing. Absent ⇒ plain register.
  handshake?: {
    playing: boolean;
    showId?: string;
    recordingId: string;
    tracks?: SessionTrack[];
    trackIndex: number;
    positionMs: number;
    durationMs?: number;
    date?: string | null;
    venue?: string | null;
    location?: string | null;
  };
}

export interface HeartbeatMessage {
  type: "heartbeat";
  // ADR-0011 Chunk B: ownership-lease renewal. The device producing audio
  // piggybacks its local playback state so the server can heal an ownerless
  // session (activeDeviceId == null) without a new explicit command. Additive/
  // optional — legacy clients omit them. Server gates on protocolVersion >= 1.
  playing?: boolean;
  recordingId?: string;
  trackIndex?: number;
  positionMs?: number;
}

export interface CommandMessage {
  type: "command";
  action: string;
  [key: string]: unknown;
}

export interface TimeSyncMessage {
  type: "time_sync";
  clientTs: number;
}

export type ClientMessage = RegisterMessage | HeartbeatMessage | CommandMessage | TimeSyncMessage;

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

export interface VolumeMessage {
  type: "volume";
  volume: number;
}

export interface VolumeReportMessage {
  type: "volume_report";
  deviceId: string;
  volume: number;
}

export interface TimeSyncReplyMessage {
  type: "time_sync";
  clientTs: number;
  serverTs: number;
}

export type ServerMessage =
  | StateMessage
  | DevicesMessage
  | ErrorMessage
  | VolumeMessage
  | VolumeReportMessage
  | TimeSyncReplyMessage;
