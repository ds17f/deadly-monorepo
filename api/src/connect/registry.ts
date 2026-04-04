import type { WebSocket } from "@fastify/websocket";
import type { ConnectDevice, PlaybackState, ActiveSession, UserPlaybackState, ConnectMessage } from "./types.js";
import { getPublisher, getSubscriber } from "../db/redis.js";
import { loadUserPlaybackState, upsertPlaybackPosition, clearPlaybackPosition } from "../db/userdata.js";

interface ConnectedDevice {
  device: ConnectDevice;
  socket: WebSocket;
}

// In-memory registry: userId → deviceId → ConnectedDevice
const registry = new Map<string, Map<string, ConnectedDevice>>();

// Per-user active playback session (legacy, kept for backward compat broadcasts)
const activeSessions = new Map<string, ActiveSession>();

// Per-user persistent playback state (new model)
const userStates = new Map<string, UserPlaybackState>();

export function registerDevice(device: ConnectDevice, socket: WebSocket): void {
  let userDevices = registry.get(device.userId);
  if (!userDevices) {
    userDevices = new Map();
    registry.set(device.userId, userDevices);
  }
  userDevices.set(device.deviceId, { device, socket });

  // Broadcast updated device list to all user's devices
  broadcastDeviceList(device.userId);

  // If no in-memory state, try loading from DB
  let state = userStates.get(device.userId) ?? null;
  if (!state) {
    state = loadUserPlaybackState(device.userId);
    if (state) {
      userStates.set(device.userId, state);
    }
  }

  // Send current user state to the newly registered device
  sendToSocket(socket, { type: "user_state", state });

  // Also send legacy active_session for backward compat
  const session = activeSessions.get(device.userId) ?? null;
  sendToSocket(socket, { type: "active_session", session });
}

export function unregisterDevice(userId: string, deviceId: string): void {
  const userDevices = registry.get(userId);
  if (!userDevices) return;
  userDevices.delete(deviceId);

  // If disconnecting device owns the active state, park it (don't delete)
  const state = userStates.get(userId);
  if (state && state.activeDeviceId === deviceId) {
    state.activeDeviceId = null;
    state.activeDeviceName = null;
    state.activeDeviceType = null;
    state.isPlaying = false;
    state.updatedAt = Date.now();

    // Persist position to DB
    upsertPlaybackPosition(userId, {
      showId: state.showId,
      recordingId: state.recordingId,
      trackIndex: state.trackIndex,
      positionMs: state.positionMs,
      date: state.date,
      venue: state.venue,
      location: state.location,
    });

    broadcastUserState(userId, state);
  }

  // Legacy: clear active session if this device owned it
  clearActiveSession(userId, deviceId);

  if (userDevices.size === 0) {
    registry.delete(userId);
  } else {
    broadcastDeviceList(userId);
  }
}

export function getDevicesForUser(userId: string): ConnectDevice[] {
  const userDevices = registry.get(userId);
  if (!userDevices) return [];
  return Array.from(userDevices.values()).map((c) => c.device);
}

export function relayPlayOn(userId: string, fromDeviceId: string, targetDeviceId: string, state: PlaybackState, fromDeviceName: string): boolean {
  const userDevices = registry.get(userId);
  if (!userDevices) return false;

  const target = userDevices.get(targetDeviceId);
  if (!target) {
    log("relay_play_on", `${fromDeviceName} → cross-server target=${targetDeviceId.slice(0, 8)}: pos=${state.positionMs}ms track=${state.trackIndex}`);
    const msg = JSON.stringify({
      type: "play_on_relay",
      userId,
      fromDeviceId,
      targetDeviceId,
      state,
      fromDeviceName,
    });
    getPublisher().publish(`connect:user:${userId}`, msg).catch(() => {});
    return true;
  }

  log("relay_play_on", `${fromDeviceName} → ${target.device.name}: pos=${state.positionMs}ms track=${state.trackIndex}`);
  sendToSocket(target.socket, {
    type: "session_play_on",
    state,
    fromDeviceName,
    relayedAt: Date.now(),
  });
  return true;
}

// ── User Playback State ────────────────────────────────────────────

const log = (tag: string, msg: string) => console.log(`[Connect] ${tag} ${msg}`);

export function updateUserState(userId: string, patch: Partial<UserPlaybackState>): void {
  let state = userStates.get(userId);
  if (state) {
    Object.assign(state, patch, { updatedAt: Date.now() });
  } else {
    // Need at least showId/recordingId to create a new state
    if (!patch.showId || !patch.recordingId) return;
    state = {
      showId: patch.showId,
      recordingId: patch.recordingId,
      trackIndex: patch.trackIndex ?? 0,
      positionMs: patch.positionMs ?? 0,
      durationMs: patch.durationMs ?? 0,
      trackTitle: patch.trackTitle,
      date: patch.date,
      venue: patch.venue,
      location: patch.location,
      activeDeviceId: patch.activeDeviceId ?? null,
      activeDeviceName: patch.activeDeviceName ?? null,
      activeDeviceType: patch.activeDeviceType ?? null,
      isPlaying: patch.isPlaying ?? false,
      updatedAt: Date.now(),
    };
  }
  userStates.set(userId, state);
  log("broadcast", `user_state: device=${state.activeDeviceName ?? "parked"} playing=${state.isPlaying} track=${state.trackIndex} pos=${state.positionMs}ms dur=${state.durationMs}ms`);
  broadcastUserState(userId, state);

  // Relay to other server instances
  getPublisher().publish(`connect:user:${userId}`, JSON.stringify({
    type: "state_relay",
    userId,
    state,
  })).catch(() => {});
}

export function getUserState(userId: string): UserPlaybackState | null {
  return userStates.get(userId) ?? null;
}

export function sendSessionStop(userId: string, deviceId: string): void {
  const userDevices = registry.get(userId);
  const device = userDevices?.get(deviceId);
  if (device) {
    log("session_stop", `→ ${device.device.name} (${device.device.type})`);
    sendToSocket(device.socket, { type: "session_stop" });
  }
}

export function deleteUserState(userId: string): void {
  userStates.delete(userId);
  clearPlaybackPosition(userId);
  broadcastUserState(userId, null);

  getPublisher().publish(`connect:user:${userId}`, JSON.stringify({
    type: "state_relay",
    userId,
    state: null,
  })).catch(() => {});
}

// ── Active Session (legacy) ────────────────────────────────────────

export function setActiveSession(userId: string, session: ActiveSession): void {
  activeSessions.set(userId, session);
  broadcastActiveSession(userId, session);
  // Relay to other server instances
  getPublisher().publish(`connect:user:${userId}`, JSON.stringify({
    type: "session_relay",
    userId,
    session,
  })).catch(() => {});
}

export function clearActiveSession(userId: string, deviceId?: string): void {
  const current = activeSessions.get(userId);
  if (!current) return;
  if (deviceId && current.deviceId !== deviceId) return;
  activeSessions.delete(userId);
  broadcastActiveSession(userId, null);
  getPublisher().publish(`connect:user:${userId}`, JSON.stringify({
    type: "session_relay",
    userId,
    session: null,
  })).catch(() => {});
}

export function getActiveSession(userId: string): ActiveSession | null {
  return activeSessions.get(userId) ?? null;
}

export function broadcastPosition(userId: string, fromDeviceId: string, state: PlaybackState): void {
  const userDevices = registry.get(userId);
  if (!userDevices) return;

  // Update legacy active session position if sender is the active device
  const session = activeSessions.get(userId);
  if (session && session.deviceId === fromDeviceId) {
    session.state.positionMs = state.positionMs;
    session.state.trackIndex = state.trackIndex;
    session.state.status = state.status;
    session.updatedAt = Date.now();
  }

  // Update user state position if sender is the active device
  const uState = userStates.get(userId);
  if (uState && uState.activeDeviceId === fromDeviceId) {
    uState.positionMs = state.positionMs;
    uState.trackIndex = state.trackIndex;
    if (state.durationMs != null) uState.durationMs = state.durationMs;
    if (state.trackTitle != null) uState.trackTitle = state.trackTitle;
    uState.updatedAt = Date.now();
  }

  const msg: ConnectMessage = {
    type: "position_update",
    state,
  };

  for (const [deviceId, { socket }] of userDevices) {
    if (deviceId !== fromDeviceId) {
      sendToSocket(socket, msg);
    }
  }
}

export function initRedisSubscriber(): void {
  const sub = getSubscriber();
  sub.psubscribe("connect:user:*").catch(() => {});

  sub.on("pmessage", (_pattern: string, _channel: string, message: string) => {
    try {
      const data = JSON.parse(message);
      if (data.type === "play_on_relay") {
        const userDevices = registry.get(data.userId);
        const target = userDevices?.get(data.targetDeviceId);
        if (target) {
          sendToSocket(target.socket, {
            type: "session_play_on",
            state: data.state,
            fromDeviceName: data.fromDeviceName,
            relayedAt: Date.now(),
          });
        }
      } else if (data.type === "session_relay") {
        // Update local cache and broadcast to local devices
        if (data.session) {
          activeSessions.set(data.userId, data.session);
        } else {
          activeSessions.delete(data.userId);
        }
        broadcastActiveSession(data.userId, data.session);
      } else if (data.type === "state_relay") {
        // Update local cache and broadcast to local devices
        if (data.state) {
          userStates.set(data.userId, data.state);
        } else {
          userStates.delete(data.userId);
        }
        broadcastUserState(data.userId, data.state);
      }
    } catch { /* ignore malformed messages */ }
  });
}

// ── Helpers ─────────────────────────────────────────────────────────

function broadcastUserState(userId: string, state: UserPlaybackState | null): void {
  const userDevices = registry.get(userId);
  if (!userDevices) return;

  const msg: ConnectMessage = { type: "user_state", state };
  for (const { socket } of userDevices.values()) {
    sendToSocket(socket, msg);
  }
}

function broadcastActiveSession(userId: string, session: ActiveSession | null): void {
  const userDevices = registry.get(userId);
  if (!userDevices) return;

  const msg: ConnectMessage = { type: "active_session", session };
  for (const { socket } of userDevices.values()) {
    sendToSocket(socket, msg);
  }
}

function broadcastDeviceList(userId: string): void {
  const userDevices = registry.get(userId);
  if (!userDevices) return;

  const devices = Array.from(userDevices.values()).map((c) => ({
    deviceId: c.device.deviceId,
    type: c.device.type,
    name: c.device.name,
    capabilities: c.device.capabilities,
  }));

  const msg: ConnectMessage = { type: "devices", devices };

  for (const { socket } of userDevices.values()) {
    sendToSocket(socket, msg);
  }
}

function sendToSocket(socket: WebSocket, msg: ConnectMessage): void {
  if (socket.readyState === 1 /* OPEN */) {
    socket.send(JSON.stringify(msg));
  }
}
