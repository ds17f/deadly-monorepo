import type { WebSocket } from "@fastify/websocket";
import type { ConnectDevice, PlaybackCommand, PlaybackState, ActiveSession, ConnectMessage } from "./types.js";
import { getPublisher, getSubscriber } from "../db/redis.js";

interface ConnectedDevice {
  device: ConnectDevice;
  socket: WebSocket;
}

// In-memory registry: userId → deviceId → ConnectedDevice
const registry = new Map<string, Map<string, ConnectedDevice>>();

// Per-user active playback session
const activeSessions = new Map<string, ActiveSession>();

export function registerDevice(device: ConnectDevice, socket: WebSocket): void {
  let userDevices = registry.get(device.userId);
  if (!userDevices) {
    userDevices = new Map();
    registry.set(device.userId, userDevices);
  }
  userDevices.set(device.deviceId, { device, socket });

  // Broadcast updated device list to all user's devices
  broadcastDeviceList(device.userId);

  // Send current active session to the newly registered device
  const session = activeSessions.get(device.userId) ?? null;
  sendToSocket(socket, { type: "active_session", session });
}

export function unregisterDevice(userId: string, deviceId: string): void {
  const userDevices = registry.get(userId);
  if (!userDevices) return;
  userDevices.delete(deviceId);

  // If disconnecting device owns the active session, clear it
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

export function relayCommand(userId: string, fromDeviceId: string, targetDeviceId: string, command: PlaybackCommand): boolean {
  const userDevices = registry.get(userId);
  if (!userDevices) return false;

  const target = userDevices.get(targetDeviceId);
  if (!target) {
    // Try Redis for multi-instance relay
    const msg = JSON.stringify({
      type: "command_relay",
      userId,
      fromDeviceId,
      targetDeviceId,
      command,
    });
    getPublisher().publish(`connect:user:${userId}`, msg).catch(() => {});
    return true;
  }

  sendToSocket(target.socket, {
    type: "command_received",
    fromDeviceId,
    command,
  });
  return true;
}

export function relayTransfer(userId: string, fromDeviceId: string, targetDeviceId: string, state: PlaybackState): boolean {
  const userDevices = registry.get(userId);
  if (!userDevices) return false;

  const target = userDevices.get(targetDeviceId);
  if (!target) {
    const msg = JSON.stringify({
      type: "transfer_relay",
      userId,
      fromDeviceId,
      targetDeviceId,
      state,
    });
    getPublisher().publish(`connect:user:${userId}`, msg).catch(() => {});
    return true;
  }

  sendToSocket(target.socket, {
    type: "transfer_received",
    fromDeviceId,
    state,
  });
  return true;
}

// ── Active Session ──────────────────────────────────────────────────

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

  // Update active session position if sender is the active device
  const session = activeSessions.get(userId);
  if (session && session.deviceId === fromDeviceId) {
    session.state.positionMs = state.positionMs;
    session.state.trackIndex = state.trackIndex;
    session.state.status = state.status;
    session.updatedAt = Date.now();
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
      if (data.type === "command_relay") {
        const userDevices = registry.get(data.userId);
        const target = userDevices?.get(data.targetDeviceId);
        if (target) {
          sendToSocket(target.socket, {
            type: "command_received",
            fromDeviceId: data.fromDeviceId,
            command: data.command,
          });
        }
      } else if (data.type === "transfer_relay") {
        const userDevices = registry.get(data.userId);
        const target = userDevices?.get(data.targetDeviceId);
        if (target) {
          sendToSocket(target.socket, {
            type: "transfer_received",
            fromDeviceId: data.fromDeviceId,
            state: data.state,
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
      }
    } catch { /* ignore malformed messages */ }
  });
}

// ── Helpers ─────────────────────────────────────────────────────────

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
