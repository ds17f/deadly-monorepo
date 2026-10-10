export type DeviceType = "ios" | "android" | "web";

export interface ConnectDevice {
  deviceId: string;
  deviceType: DeviceType;
  deviceName: string;
}

export interface SessionTrack {
  title: string;
  durationMs: number;
}

export interface ConnectState {
  version: number;
  // Server boot id — a change means the server restarted (see api ConnectState.epoch).
  epoch: number;
  showId: string | null;
  recordingId: string | null;
  tracks: SessionTrack[];
  trackIndex: number;
  positionMs: number;
  positionTs: number;
  durationMs: number;
  // ADR-0017: bumped ONLY by an explicit `seek` command, never by routine
  // position reports. The active client honors a remote position when this
  // advances (intent, not magnitude), so a self-echo of its own ~5s position
  // report can't trigger a self-seek. Coalesce a missing value to 0.
  seekNonce: number;
  // ADR-0019: bumped ONLY by an explicit `next`/`prev`, never by a `load` or a
  // position report. The active client follows a remote track change when this
  // advances (not on trackIndex coincidence). Coalesce a missing value to 0.
  trackNonce: number;
  playing: boolean;
  activeDeviceId: string | null;
  activeDeviceName: string | null;
  activeDeviceType: DeviceType | null;
  date: string | null;
  venue: string | null;
  location: string | null;
  // ADR-0010 §7: shared end-of-show countdown. Optional/additive.
  pendingAdvance?: { showId: string; deadline: number } | null;
}
