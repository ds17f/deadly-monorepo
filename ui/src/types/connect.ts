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
