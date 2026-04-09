import type { WebSocket } from "ws";
import type { ConnectState, ConnectDevice, DeviceType, SessionTrack } from "./types.js";
import { upsertPlaybackPosition } from "../db/userdata.js";

const log = (msg: string) => console.log(`[Connect] ${msg}`);
const warn = (msg: string) => console.warn(`[Connect] ${msg}`);

interface LiveDevice {
  device: ConnectDevice;
  socket: WebSocket;
}

// userId -> ConnectState
const userStates = new Map<string, ConnectState>();

// `${userId}:${deviceId}` -> LiveDevice
const liveDevices = new Map<string, LiveDevice>();

interface PendingTransfer {
  targetDeviceId: string;
  interpolatedPositionMs: number;
  timeoutHandle: ReturnType<typeof setTimeout>;
}

// userId -> PendingTransfer (at most one pending transfer per user)
const pendingTransfers = new Map<string, PendingTransfer>();

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
    const msg = JSON.stringify(payload);
    log(`sendJson: readyState=OPEN, sending ${msg.length} bytes, type=${(payload as Record<string, unknown>)?.type}`);
    socket.send(msg);
  } else {
    warn(`sendJson: SKIPPED — readyState=${socket.readyState} (not OPEN), type=${(payload as Record<string, unknown>)?.type}`);
  }
}

export function broadcastState(userId: string, state: ConnectState): void {
  const msg = JSON.stringify({ type: "state", state });
  let sent = 0;
  let skipped = 0;
  for (const [key, entry] of liveDevices) {
    if (key.startsWith(`${userId}:`)) {
      if (entry.socket.readyState === entry.socket.OPEN) {
        entry.socket.send(msg);
        sent++;
      } else {
        warn(`broadcastState: skipping ${entry.device.name}[${entry.device.type}] — readyState=${entry.socket.readyState}`);
        skipped++;
      }
    }
  }
  log(`broadcastState: v${state.version} show=${state.showId} playing=${state.playing} — sent=${sent} skipped=${skipped}`);
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
  let sent = 0;
  for (const [key, entry] of liveDevices) {
    if (key.startsWith(`${userId}:`) && entry.socket.readyState === entry.socket.OPEN) {
      entry.socket.send(msg);
      sent++;
    }
  }
  log(`broadcastDevices: ${devices.map(d => `${d.deviceName}[${d.deviceType}]`).join(", ")} — sent to ${sent} sockets`);
}

export function mutate(userId: string, patch: Partial<ConnectState>): void {
  const state = userStates.get(userId);
  if (!state) return;
  const patchKeys = Object.keys(patch).join(",");
  Object.assign(state, patch);
  // Invariant: cannot be playing without an active device
  if (state.playing && !state.activeDeviceId) {
    state.playing = false;
  }
  state.version++;
  log(`mutate: v${state.version} patch=[${patchKeys}] playing=${state.playing} activeDevice=${state.activeDeviceId}`);
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
  log(`registerDevice: ${name}[${type}] (${deviceId}) — sending state v${state.version} show=${state.showId} rec=${state.recordingId} track=${state.trackIndex}/${state.tracks.length} pos=${state.positionMs} playing=${state.playing}`);
  sendJson(socket, { type: "state", state });
  broadcastDevices(userId);
}

export function unregisterDevice(userId: string, deviceId: string): void {
  const key = deviceKey(userId, deviceId);
  const entry = liveDevices.get(key);
  if (!entry) return; // idempotent

  log(`unregisterDevice: ${entry.device.name}[${entry.device.type}] (${deviceId})`);
  liveDevices.delete(key);

  // Cancel pending transfer if this device was the target
  const pending = pendingTransfers.get(userId);
  if (pending && pending.targetDeviceId === deviceId) {
    clearTimeout(pending.timeoutHandle);
    pendingTransfers.delete(userId);
    log(`unregisterDevice: cancelled pending transfer — target ${deviceId} disconnected`);
  }

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
      // Cancel pending transfer if evicted device was the target
      const pending = pendingTransfers.get(userId);
      if (pending && deviceIds.includes(pending.targetDeviceId)) {
        clearTimeout(pending.timeoutHandle);
        pendingTransfers.delete(userId);
        log(`heartbeatSweep: cancelled pending transfer — target evicted`);
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

export function handleSeek(userId: string, params: { trackIndex: number; positionMs: number; durationMs?: number }): void {
  const state = userStates.get(userId);
  if (!state || !state.showId) return;

  const patch: Partial<ConnectState> = {
    trackIndex: params.trackIndex,
    positionMs: params.positionMs,
    positionTs: Date.now(),
  };
  if (typeof params.durationMs === "number") {
    patch.durationMs = params.durationMs;
  }

  log(`handleSeek: track=${params.trackIndex} pos=${params.positionMs} dur=${params.durationMs ?? "unchanged"}`);
  mutate(userId, patch);

  if (state.showId && state.recordingId) {
    upsertPlaybackPosition(userId, {
      showId: state.showId,
      recordingId: state.recordingId,
      trackIndex: params.trackIndex,
      positionMs: params.positionMs,
      date: state.date ?? undefined,
      venue: state.venue ?? undefined,
      location: state.location ?? undefined,
    });
  }
}

function activateTarget(userId: string, targetDeviceId: string, positionMs: number): void {
  const targetEntry = liveDevices.get(deviceKey(userId, targetDeviceId));
  if (!targetEntry) {
    log(`activateTarget: target ${targetDeviceId} no longer connected, staying parked`);
    return;
  }

  mutate(userId, {
    activeDeviceId: targetDeviceId,
    activeDeviceName: targetEntry.device.name,
    activeDeviceType: targetEntry.device.type,
    playing: true,
    positionMs,
    positionTs: Date.now(),
  });
}

export function handleTransfer(
  userId: string,
  deviceId: string,
  socket: WebSocket,
  targetDeviceId: string,
): void {
  const state = userStates.get(userId);
  if (!state) return;

  // Validate target exists
  const targetEntry = liveDevices.get(deviceKey(userId, targetDeviceId));
  if (!targetEntry) {
    sendJson(socket, { type: "error", message: "Target device not found", state });
    return;
  }

  // Self-transfer when already active: no-op
  if (state.activeDeviceId === targetDeviceId) return;

  // Cancel any existing pending transfer
  const existing = pendingTransfers.get(userId);
  if (existing) {
    clearTimeout(existing.timeoutHandle);
    pendingTransfers.delete(userId);
  }

  // No active device (parked): skip phase 1, go directly to phase 2
  if (!state.activeDeviceId) {
    log(`handleTransfer: parked — activating ${targetEntry.device.name}[${targetEntry.device.type}] directly`);
    activateTarget(userId, targetDeviceId, state.positionMs);
    return;
  }

  // Phase 1 — Park the old device
  const now = Date.now();
  const interpolatedPositionMs = state.playing
    ? state.positionMs + (now - state.positionTs)
    : state.positionMs;

  log(`handleTransfer: phase 1 — parking, interpolated position ${interpolatedPositionMs}ms`);

  mutate(userId, {
    activeDeviceId: null,
    activeDeviceName: null,
    activeDeviceType: null,
    playing: false,
    positionMs: interpolatedPositionMs,
    positionTs: now,
  });

  // Set 1s timeout for phase 2 fallback
  const timeoutHandle = setTimeout(() => {
    const pending = pendingTransfers.get(userId);
    if (!pending) return;
    pendingTransfers.delete(userId);
    log(`handleTransfer: timeout — proceeding with interpolated position ${pending.interpolatedPositionMs}ms`);
    activateTarget(userId, pending.targetDeviceId, pending.interpolatedPositionMs);
  }, 1000);

  pendingTransfers.set(userId, {
    targetDeviceId,
    interpolatedPositionMs,
    timeoutHandle,
  });
}

export function handlePosition(userId: string, deviceId: string, positionMs: number): void {
  const state = userStates.get(userId);
  if (!state) return;

  // Check if this completes a pending transfer (old device reporting final position)
  const pending = pendingTransfers.get(userId);
  if (pending) {
    clearTimeout(pending.timeoutHandle);
    pendingTransfers.delete(userId);
    log(`handleTransfer: phase 2 — old device reported positionMs=${positionMs}`);
    activateTarget(userId, pending.targetDeviceId, positionMs);
    return;
  }

  // Normal position report — only accept from active device
  if (state.activeDeviceId !== deviceId) return;

  state.positionMs = positionMs;
  state.positionTs = Date.now();
}

export function stopHeartbeatSweep(): void {
  if (sweepInterval) {
    clearInterval(sweepInterval);
    sweepInterval = null;
  }
}
