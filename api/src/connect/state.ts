import type { WebSocket } from "ws";
import type { ConnectState, ConnectDevice, DeviceType, SessionTrack } from "./types.js";
import { upsertPlaybackPosition } from "../db/userdata.js";

interface LiveDevice {
  device: ConnectDevice;
  socket: WebSocket;
}

// userId -> ConnectState
const userStates = new Map<string, ConnectState>();

// `${userId}:${deviceId}` -> LiveDevice
const liveDevices = new Map<string, LiveDevice>();

let sweepInterval: ReturnType<typeof setInterval> | null = null;

function deviceKey(userId: string, deviceId: string): string {
  return `${userId}:${deviceId}`;
}

function initialState(): ConnectState {
  return {
    version: 0,
    showId: null,
    recordingId: null,
    tracks: [],
    trackIndex: 0,
    positionMs: 0,
    positionTs: Date.now(),
    durationMs: 0,
    playing: false,
    activeDeviceId: null,
    activeDeviceName: null,
    activeDeviceType: null,
    date: null,
    venue: null,
    location: null,
  };
}

export function getOrCreateState(userId: string): ConnectState {
  let state = userStates.get(userId);
  if (!state) {
    state = initialState();
    userStates.set(userId, state);
  }
  return state;
}

function sendJson(socket: WebSocket, payload: unknown): void {
  if (socket.readyState === socket.OPEN) {
    socket.send(JSON.stringify(payload));
  }
}

export function broadcastState(userId: string, state: ConnectState): void {
  const msg = JSON.stringify({ type: "state", state });
  for (const [key, entry] of liveDevices) {
    if (key.startsWith(`${userId}:`) && entry.socket.readyState === entry.socket.OPEN) {
      entry.socket.send(msg);
    }
  }
}

export function broadcastDevices(userId: string): void {
  const devices: Array<{ deviceId: string; deviceType: DeviceType; deviceName: string }> = [];
  for (const [key, entry] of liveDevices) {
    if (key.startsWith(`${userId}:`)) {
      devices.push({
        deviceId: entry.device.deviceId,
        deviceType: entry.device.type,
        deviceName: entry.device.name,
      });
    }
  }
  const msg = JSON.stringify({ type: "devices", devices });
  for (const [key, entry] of liveDevices) {
    if (key.startsWith(`${userId}:`) && entry.socket.readyState === entry.socket.OPEN) {
      entry.socket.send(msg);
    }
  }
}

export function mutate(userId: string, patch: Partial<ConnectState>): void {
  const state = userStates.get(userId);
  if (!state) return;
  Object.assign(state, patch);
  // Invariant: cannot be playing without an active device
  if (state.playing && !state.activeDeviceId) {
    state.playing = false;
  }
  state.version++;
  broadcastState(userId, state);
}

export function registerDevice(
  userId: string,
  deviceId: string,
  type: DeviceType,
  name: string,
  socket: WebSocket,
): void {
  const key = deviceKey(userId, deviceId);

  // Close any existing socket for this deviceId (reconnect case)
  const existing = liveDevices.get(key);
  if (existing && existing.socket !== socket) {
    existing.socket.close(4000, "replaced by new connection");
  }

  const device: ConnectDevice = {
    deviceId,
    userId,
    type,
    name,
    lastHeartbeat: Date.now(),
  };

  liveDevices.set(key, { device, socket });

  const state = getOrCreateState(userId);
  sendJson(socket, { type: "state", state });
  broadcastDevices(userId);
}

export function unregisterDevice(userId: string, deviceId: string): void {
  const key = deviceKey(userId, deviceId);
  if (!liveDevices.has(key)) return; // idempotent

  liveDevices.delete(key);

  const state = userStates.get(userId);
  if (state && state.activeDeviceId === deviceId) {
    // Snapshot position before clearing active device
    if (state.showId && state.recordingId && state.playing) {
      const now = Date.now();
      const snapshotMs = state.positionMs + (now - state.positionTs);
      upsertPlaybackPosition(userId, {
        showId: state.showId,
        recordingId: state.recordingId,
        trackIndex: state.trackIndex,
        positionMs: snapshotMs,
        date: state.date ?? undefined,
        venue: state.venue ?? undefined,
        location: state.location ?? undefined,
      });
    }

    mutate(userId, {
      activeDeviceId: null,
      activeDeviceName: null,
      activeDeviceType: null,
      playing: false,
    });
  }

  broadcastDevices(userId);
}

export function handleHeartbeat(userId: string, deviceId: string): void {
  const entry = liveDevices.get(deviceKey(userId, deviceId));
  if (entry) {
    entry.device.lastHeartbeat = Date.now();
  }
}

export function startHeartbeatSweep(): void {
  if (sweepInterval) return;
  sweepInterval = setInterval(() => {
    const now = Date.now();
    const evictedByUser = new Map<string, string[]>(); // userId -> deviceIds

    for (const [key, entry] of liveDevices) {
      if (now - entry.device.lastHeartbeat > 45_000) {
        const { userId, deviceId } = entry.device;

        // Remove from map first so unregisterDevice (triggered by close) is a no-op
        liveDevices.delete(key);
        entry.socket.close(4001, "heartbeat timeout");

        if (!evictedByUser.has(userId)) {
          evictedByUser.set(userId, []);
        }
        evictedByUser.get(userId)!.push(deviceId);
      }
    }

    for (const [userId, deviceIds] of evictedByUser) {
      const state = userStates.get(userId);
      if (state && state.activeDeviceId && deviceIds.includes(state.activeDeviceId)) {
        mutate(userId, {
          activeDeviceId: null,
          activeDeviceName: null,
          activeDeviceType: null,
          playing: false,
        });
      }
      broadcastDevices(userId);
    }
  }, 10_000);
}

export interface LoadParams {
  showId: string;
  recordingId: string;
  tracks: SessionTrack[];
  trackIndex: number;
  positionMs: number;
  durationMs: number;
  date?: string | null;
  venue?: string | null;
  location?: string | null;
  autoplay?: boolean;
}

export function handleLoad(userId: string, deviceId: string, socket: WebSocket, params: LoadParams): void {
  const state = userStates.get(userId);
  if (!state) return;

  const patch: Partial<ConnectState> = {
    showId: params.showId,
    recordingId: params.recordingId,
    tracks: params.tracks,
    trackIndex: params.trackIndex,
    positionMs: params.positionMs,
    positionTs: Date.now(),
    durationMs: params.durationMs,
    date: params.date ?? null,
    venue: params.venue ?? null,
    location: params.location ?? null,
  };

  if (params.autoplay) {
    if (!state.activeDeviceId) {
      const entry = liveDevices.get(deviceKey(userId, deviceId));
      if (entry) {
        patch.activeDeviceId = deviceId;
        patch.activeDeviceName = entry.device.name;
        patch.activeDeviceType = entry.device.type;
      }
    }
    patch.playing = true;
  }

  mutate(userId, patch);

  upsertPlaybackPosition(userId, {
    showId: params.showId,
    recordingId: params.recordingId,
    trackIndex: params.trackIndex,
    positionMs: params.positionMs,
    date: params.date ?? undefined,
    venue: params.venue ?? undefined,
    location: params.location ?? undefined,
  });
}

export function handlePlay(userId: string, deviceId: string, socket: WebSocket): void {
  const state = userStates.get(userId);
  if (!state) return;

  if (!state.showId) {
    sendJson(socket, { type: "error", message: "Nothing loaded", state });
    return;
  }

  if (state.playing) return;

  const patch: Partial<ConnectState> = {
    playing: true,
    positionTs: Date.now(),
  };

  if (!state.activeDeviceId) {
    const entry = liveDevices.get(deviceKey(userId, deviceId));
    if (!entry) return;
    patch.activeDeviceId = deviceId;
    patch.activeDeviceName = entry.device.name;
    patch.activeDeviceType = entry.device.type;
  }

  mutate(userId, patch);
}

export function handlePause(userId: string): void {
  const state = userStates.get(userId);
  if (!state) return;

  if (!state.activeDeviceId || !state.playing) return;

  const now = Date.now();
  const newPositionMs = state.positionMs + (now - state.positionTs);

  mutate(userId, {
    playing: false,
    positionMs: newPositionMs,
    positionTs: now,
  });

  if (state.showId && state.recordingId) {
    upsertPlaybackPosition(userId, {
      showId: state.showId,
      recordingId: state.recordingId,
      trackIndex: state.trackIndex,
      positionMs: newPositionMs,
      date: state.date ?? undefined,
      venue: state.venue ?? undefined,
      location: state.location ?? undefined,
    });
  }
}

export function stopHeartbeatSweep(): void {
  if (sweepInterval) {
    clearInterval(sweepInterval);
    sweepInterval = null;
  }
}
