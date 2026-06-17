import type { WebSocket } from "ws";
import type { ConnectState, ConnectDevice, DeviceType, SessionTrack } from "./types.js";
import { LEGACY_PROTOCOL_VERSION } from "./types.js";
import { upsertPlaybackPosition as dbUpsertPlaybackPosition, getPlaybackPosition } from "../db/userdata.js";

const log = (msg: string) => console.log(`[Connect] ${msg}`);
const warn = (msg: string) => console.warn(`[Connect] ${msg}`);

// Persisting playback position from the Connect WS path is best-effort: a DB
// error (e.g. an FK failure when the user/show isn't in this DB) must never
// escape a socket message handler — an unhandled throw there crashes the whole
// process. Swallow-and-log here; the REST route keeps the throwing version
// (dbUpsertPlaybackPosition) so an HTTP write still surfaces a 500.
function upsertPlaybackPosition(
  userId: string,
  pos: Parameters<typeof dbUpsertPlaybackPosition>[1],
): void {
  try {
    dbUpsertPlaybackPosition(userId, pos);
  } catch (err) {
    warn(`persist position failed for ${userId}: ${(err as Error).message}`);
  }
}

// Server boot id, fixed for the life of this process. Stamped into every
// session's state; a change tells clients the server restarted (see ConnectState.epoch).
const SERVER_EPOCH = Date.now();

interface LiveDevice {
  device: ConnectDevice;
  socket: WebSocket;
  // Per-CONNECTION facts (ADR-0011 §3). Same lifetime as the socket: stamped on
  // `register`, valid until close, re-sent on every reconnect (self-refreshing,
  // never persisted). `protocolVersion` is the wire contract the server may
  // branch on; `appVersion` is build identity for telemetry only.
  protocolVersion: number;
  appVersion: string | null;
}

// userId -> ConnectState
const userStates = new Map<string, ConnectState>();

// `${userId}:${deviceId}` -> LiveDevice
const liveDevices = new Map<string, LiveDevice>();

interface PendingTransfer {
  targetDeviceId: string;
  interpolatedPositionMs: number;
  wasPlaying: boolean;
  timeoutHandle: ReturnType<typeof setTimeout>;
}

// userId -> PendingTransfer (at most one pending transfer per user)
const pendingTransfers = new Map<string, PendingTransfer>();

let sweepInterval: ReturnType<typeof setInterval> | null = null;

function deviceKey(userId: string, deviceId: string): string {
  return `${userId}:${deviceId}`;
}

function persistCurrent(userId: string, state: ConnectState): void {
  if (!state.showId || !state.recordingId) return;
  const now = Date.now();
  const positionMs = state.playing
    ? state.positionMs + (now - state.positionTs)
    : state.positionMs;
  upsertPlaybackPosition(userId, {
    showId: state.showId,
    recordingId: state.recordingId,
    trackIndex: state.trackIndex,
    positionMs,
    date: state.date ?? undefined,
    venue: state.venue ?? undefined,
    location: state.location ?? undefined,
    updatedAt: Date.now(),
  });
}

function initialState(): ConnectState {
  return {
    // Seed the version from wall-clock ms, not 0. The version map is in-memory
    // and wiped on restart; seeding from a monotonic clock guarantees a fresh
    // session's version exceeds any value a still-connected client held before
    // the restart, so their `version <= current` guard accepts the new state
    // even if the client predates the reset-on-reconnect fix. (Increments can't
    // catch up to wall-clock ms at any realistic mutation rate.)
    version: Date.now(),
    epoch: SERVER_EPOCH,
    showId: null,
    recordingId: null,
    tracks: [],
    trackIndex: 0,
    positionMs: 0,
    positionTs: Date.now(),
    durationMs: 0,
    seekNonce: 0,
    playing: false,
    activeDeviceId: null,
    activeDeviceName: null,
    activeDeviceType: null,
    date: null,
    venue: null,
    location: null,
    pendingAdvance: null,
  };
}

export function getOrCreateState(userId: string): ConnectState {
  let state = userStates.get(userId);
  if (!state) {
    state = initialState();
    const persisted = getPlaybackPosition(userId);
    if (persisted) {
      state.showId = persisted.showId;
      state.recordingId = persisted.recordingId;
      state.trackIndex = persisted.trackIndex;
      state.positionMs = persisted.positionMs;
      state.date = persisted.date ?? null;
      state.venue = persisted.venue ?? null;
      state.location = persisted.location ?? null;
      log(`hydrate: ${userId} show=${persisted.showId} track=${persisted.trackIndex} pos=${persisted.positionMs}`);
    }
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

// ADR-0017 test affordance: artificially delay state delivery to inflate the
// position-echo round-trip past the old 2s seek threshold, so the self-skip bug can
// be reproduced on a SINGLE device against a local server (no flaky network needed).
// Keep the delay under the ~6s pending-command timeout so self-seek suppression still
// holds. Unset in prod → zero delay, normal synchronous send.
const BROADCAST_DELAY_MS = Number(process.env.CONNECT_BROADCAST_DELAY_MS) || 0;
const BROADCAST_JITTER_MS = Number(process.env.CONNECT_BROADCAST_JITTER_MS) || 0;

export function broadcastState(userId: string, state: ConnectState): void {
  const msg = JSON.stringify({ type: "state", state });
  let sent = 0;
  let skipped = 0;
  for (const [key, entry] of liveDevices) {
    if (key.startsWith(`${userId}:`)) {
      if (entry.socket.readyState === entry.socket.OPEN) {
        const delay = BROADCAST_DELAY_MS + Math.random() * BROADCAST_JITTER_MS;
        if (delay > 0) {
          const socket = entry.socket;
          setTimeout(() => {
            if (socket.readyState === socket.OPEN) socket.send(msg);
          }, delay);
        } else {
          entry.socket.send(msg);
        }
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
  protocolVersion: number = LEGACY_PROTOCOL_VERSION,
  appVersion: string | null = null,
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

  liveDevices.set(key, { device, socket, protocolVersion, appVersion });

  const state = getOrCreateState(userId);
  log(`registerDevice: ${name}[${type}] (${deviceId}) proto=${protocolVersion} app=${appVersion ?? "?"} — sending state v${state.version} show=${state.showId} rec=${state.recordingId} track=${state.trackIndex}/${state.tracks.length} pos=${state.positionMs} playing=${state.playing}`);
  logProtocolDistribution(userId);
  sendJson(socket, { type: "state", state });
  broadcastDevices(userId);
}

// Telemetry (ADR-0011 §3, Chunk A): per-session distribution of the connected
// devices' protocol/app versions. This is what licenses retiring a legacy
// branch / advancing MIN_SUPPORTED later — on evidence, not hope.
function logProtocolDistribution(userId: string): void {
  const byProto = new Map<number, string[]>();
  for (const [k, entry] of liveDevices) {
    if (!k.startsWith(`${userId}:`)) continue;
    const tag = `${entry.device.type}${entry.appVersion ? `@${entry.appVersion}` : ""}`;
    const list = byProto.get(entry.protocolVersion) ?? [];
    list.push(tag);
    byProto.set(entry.protocolVersion, list);
  }
  const dist = [...byProto.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([proto, tags]) => `proto${proto}×${tags.length}[${tags.join(",")}]`)
    .join(" ");
  log(`protocolDistribution: ${userId} — ${dist}`);
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
    persistCurrent(userId, state);

    mutate(userId, {
      activeDeviceId: null,
      activeDeviceName: null,
      activeDeviceType: null,
      playing: false,
      // The device that would have advanced is gone — drop the countdown note.
      pendingAdvance: null,
    });
  }

  broadcastDevices(userId);
}

export interface LeaseRenewal {
  playing: boolean;
  recordingId: string;
  trackIndex: number;
  positionMs: number;
}

export function handleHeartbeat(userId: string, deviceId: string, lease?: LeaseRenewal): void {
  const entry = liveDevices.get(deviceKey(userId, deviceId));
  if (!entry) return;
  entry.device.lastHeartbeat = Date.now();

  // ── ADR-0011 Chunk B: ownership lease ──────────────────────────────────────
  // The audio-producing device heals an ownerless session by claiming it on its
  // heartbeat. This is the convergent recovery for the restart/disconnect
  // "ghost" — server believes activeDeviceId == null while a device is in fact
  // still playing — replacing the cause-specific client reclaim heuristics
  // (those stay until Chunk C proves the lease covers them). Conservative rule
  // (ADR §2): the renewal FILLS A VACUUM, it never preempts an existing owner.
  if (!lease) return;                       // legacy heartbeat — no lease payload
  if (entry.protocolVersion < 1) return;    // gated on the Chunk A primitive

  const state = userStates.get(userId);
  if (!state || !state.recordingId) return;          // no session to heal
  if (lease.recordingId !== state.recordingId) return; // device is on a different recording

  // Owner: its lease renews implicitly via lastHeartbeat above; ownership already
  // held, so nothing to broadcast. (A paused owner keeps the lease — ADR §2.)
  if (state.activeDeviceId === deviceId) return;

  // A transfer is mid-flight for this user (the server parked the source and is
  // about to activate the target — activeDeviceId is transiently null). Don't let
  // the source's lease re-claim the parked session and fight the handoff.
  if (pendingTransfers.has(userId)) return;

  // Vacuum claim: only a device actually producing audio (playing) fills an
  // ownerless session. Requiring `playing` disambiguates a parked-but-still-
  // loaded device (playing=false, e.g. transferred away) from the real source,
  // so it can't steal ownership back after a restart.
  if (state.activeDeviceId === null && lease.playing) {
    log(`handleHeartbeat: lease claim — ${entry.device.name}[${entry.device.type}] heals ownerless session rec=${lease.recordingId} track=${lease.trackIndex} pos=${lease.positionMs}`);
    mutate(userId, {
      activeDeviceId: deviceId,
      activeDeviceName: entry.device.name,
      activeDeviceType: entry.device.type,
      playing: true,
      // Heal to the device's OWN track/pos, not the server's stored pointer. The
      // server only learns trackIndex from explicit commands, so after a natural
      // auto-advance (which doesn't emit one) its trackIndex is stale. Trusting it
      // here would snap the still-playing device backward. The producing device is
      // the transport authority — follow it.
      trackIndex: lease.trackIndex,
      positionMs: lease.positionMs,
      positionTs: Date.now(),
    });
    return;
  }

  // Otherwise owned by another device (or ownerless + paused): back off — the
  // lease never preempts. Split-brain (two devices both playing the same
  // recording with no owner) is the rare path; first heartbeat wins, the other
  // backs off here.
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
        persistCurrent(userId, state);
        mutate(userId, {
          activeDeviceId: null,
          activeDeviceName: null,
          activeDeviceType: null,
          playing: false,
          pendingAdvance: null,
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
    // A new show loading supersedes any pending end-of-show countdown.
    pendingAdvance: null,
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
    updatedAt: Date.now(),
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
      updatedAt: Date.now(),
    });
  }
}

export function handleStop(userId: string): void {
  const state = userStates.get(userId);
  if (!state || !state.showId) return;

  persistCurrent(userId, state);

  const now = Date.now();
  const positionMs = state.playing
    ? state.positionMs + (now - state.positionTs)
    : state.positionMs;

  mutate(userId, {
    playing: false,
    activeDeviceId: null,
    activeDeviceName: null,
    activeDeviceType: null,
    positionMs,
    positionTs: now,
    pendingAdvance: null,
  });
}

// ── ADR-0010 §7: end-of-show countdown (cross-device) ────────────────────────

/**
 * The active device finished a show and is counting down to [showId] at
 * [deadline]. Parks playback (so it isn't dragged back) and sets the shared
 * note every device renders. Only the active device announces its own end.
 */
export function handleAnnounceNext(
  userId: string,
  deviceId: string,
  params: { showId: string; deadline: number },
): void {
  const state = userStates.get(userId);
  if (!state) return;
  if (state.activeDeviceId && state.activeDeviceId !== deviceId) return;
  log(`handleAnnounceNext: next=${params.showId} deadline=${params.deadline}`);

  const patch: Partial<ConnectState> = {
    playing: false,
    pendingAdvance: { showId: params.showId, deadline: params.deadline },
  };

  // The announcing device just finished playing locally, so it IS the active
  // device — even if the server lost that (e.g. in-memory state wiped on a
  // restart while the device kept playing across the reconnect, leaving it a
  // connected-but-not-active ghost). Claim it here like handlePlay/handleLoad
  // do; otherwise the note broadcasts but no device counts as active, so nobody
  // fires the advance at the deadline and the countdown dies silently.
  if (!state.activeDeviceId) {
    const entry = liveDevices.get(deviceKey(userId, deviceId));
    if (entry) {
      patch.activeDeviceId = deviceId;
      patch.activeDeviceName = entry.device.name;
      patch.activeDeviceType = entry.device.type;
    }
  }

  mutate(userId, patch);
}

/** Anyone cancels the pending advance — clears the note; stays parked. */
export function handleCancelAdvance(userId: string): void {
  const state = userStates.get(userId);
  if (!state || !state.pendingAdvance) return;
  log(`handleCancelAdvance: clearing pendingAdvance`);
  mutate(userId, { pendingAdvance: null });
}

/**
 * "Play now" from anywhere — move the deadline to now so the active device's
 * uniform rule (advance when present && now >= deadline) fires immediately.
 */
export function handleAdvanceNow(userId: string): void {
  const state = userStates.get(userId);
  if (!state || !state.pendingAdvance) return;
  log(`handleAdvanceNow: deadline -> now for ${state.pendingAdvance.showId}`);
  mutate(userId, {
    pendingAdvance: { showId: state.pendingAdvance.showId, deadline: Date.now() },
  });
}

export function handleSeek(userId: string, params: { trackIndex: number; positionMs: number; durationMs?: number }): void {
  const state = userStates.get(userId);
  if (!state || !state.showId) return;

  const patch: Partial<ConnectState> = {
    trackIndex: params.trackIndex,
    positionMs: params.positionMs,
    positionTs: Date.now(),
    // Mark this as a genuine seek INTENT so the active client honors it (vs a
    // routine position report, which leaves seekNonce untouched). See ADR-0017.
    seekNonce: state.seekNonce + 1,
  };
  if (typeof params.durationMs === "number") {
    patch.durationMs = params.durationMs;
  }

  log(`handleSeek: track=${params.trackIndex} pos=${params.positionMs} dur=${params.durationMs ?? "unchanged"} seekNonce=${state.seekNonce + 1}`);
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
      updatedAt: Date.now(),
    });
  }
}

export function handleNext(userId: string): void {
  const state = userStates.get(userId);
  if (!state || !state.showId || state.tracks.length === 0) return;

  const newIndex = state.trackIndex + 1;
  if (newIndex >= state.tracks.length) return;

  const track = state.tracks[newIndex];
  log(`handleNext: track=${newIndex}/${state.tracks.length}`);
  mutate(userId, {
    trackIndex: newIndex,
    positionMs: 0,
    positionTs: Date.now(),
    durationMs: track.durationMs,
  });

  if (state.showId && state.recordingId) {
    upsertPlaybackPosition(userId, {
      showId: state.showId,
      recordingId: state.recordingId,
      trackIndex: newIndex,
      positionMs: 0,
      date: state.date ?? undefined,
      venue: state.venue ?? undefined,
      location: state.location ?? undefined,
      updatedAt: Date.now(),
    });
  }
}

export function handlePrev(userId: string): void {
  const state = userStates.get(userId);
  if (!state || !state.showId || state.tracks.length === 0) return;

  const newIndex = state.trackIndex - 1;
  if (newIndex < 0) return;

  const track = state.tracks[newIndex];
  log(`handlePrev: track=${newIndex}/${state.tracks.length}`);
  mutate(userId, {
    trackIndex: newIndex,
    positionMs: 0,
    positionTs: Date.now(),
    durationMs: track.durationMs,
  });

  if (state.showId && state.recordingId) {
    upsertPlaybackPosition(userId, {
      showId: state.showId,
      recordingId: state.recordingId,
      trackIndex: newIndex,
      positionMs: 0,
      date: state.date ?? undefined,
      venue: state.venue ?? undefined,
      location: state.location ?? undefined,
      updatedAt: Date.now(),
    });
  }
}

function activateTarget(userId: string, targetDeviceId: string, positionMs: number, playing: boolean): void {
  const targetEntry = liveDevices.get(deviceKey(userId, targetDeviceId));
  if (!targetEntry) {
    log(`activateTarget: target ${targetDeviceId} no longer connected, staying parked`);
    return;
  }

  mutate(userId, {
    activeDeviceId: targetDeviceId,
    activeDeviceName: targetEntry.device.name,
    activeDeviceType: targetEntry.device.type,
    playing,
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
    activateTarget(userId, targetDeviceId, state.positionMs, state.playing);
    return;
  }

  // Phase 1 — Park the old device
  const now = Date.now();
  const wasPlaying = state.playing;
  const interpolatedPositionMs = wasPlaying
    ? state.positionMs + (now - state.positionTs)
    : state.positionMs;

  log(`handleTransfer: phase 1 — parking, interpolated position ${interpolatedPositionMs}ms, wasPlaying=${wasPlaying}`);

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
    activateTarget(userId, pending.targetDeviceId, pending.interpolatedPositionMs, pending.wasPlaying);
  }, 1000);

  pendingTransfers.set(userId, {
    targetDeviceId,
    interpolatedPositionMs,
    wasPlaying,
    timeoutHandle,
  });
}

export function handlePosition(userId: string, deviceId: string, positionMs: number, durationMs?: number): void {
  const state = userStates.get(userId);
  if (!state) return;

  // Check if this completes a pending transfer (old device reporting final position)
  const pending = pendingTransfers.get(userId);
  if (pending) {
    clearTimeout(pending.timeoutHandle);
    pendingTransfers.delete(userId);
    log(`handleTransfer: phase 2 — old device reported positionMs=${positionMs}, wasPlaying=${pending.wasPlaying}`);
    activateTarget(userId, pending.targetDeviceId, positionMs, pending.wasPlaying);
    return;
  }

  // Normal position report — only accept from active device
  if (state.activeDeviceId !== deviceId) return;

  const patch: Partial<ConnectState> = { positionMs, positionTs: Date.now() };
  // Carry the active device's real track duration so CONTROLLERS have a valid scale
  // for the scrubber + seek math. A hydrated/restored session has durationMs=0, and
  // load/seek are the only other writers — without this a controller computes
  // `fraction * 0 = 0` and every drag seeks to track start. Only overwrite with a
  // real (>0) value, and only when it actually changed (avoid pointless broadcasts).
  if (typeof durationMs === "number" && durationMs > 0 && durationMs !== state.durationMs) {
    patch.durationMs = durationMs;
  }
  mutate(userId, patch);
}

export function handleVolume(userId: string, deviceId: string, socket: WebSocket, volume: number): void {
  const state = userStates.get(userId);
  if (!state || !state.activeDeviceId) return;

  const entry = liveDevices.get(deviceKey(userId, state.activeDeviceId));
  if (!entry) return;

  log(`handleVolume: relaying volume=${volume} from ${deviceId} to active device ${state.activeDeviceId}`);
  sendJson(entry.socket, { type: "volume", volume });
}

export function handleVolumeReport(userId: string, deviceId: string, volume: number): void {
  log(`handleVolumeReport: device ${deviceId} reported volume=${volume}, broadcasting to others`);
  for (const [key, entry] of liveDevices) {
    if (key.startsWith(`${userId}:`) && !key.endsWith(`:${deviceId}`)) {
      sendJson(entry.socket, { type: "volume_report", deviceId, volume });
    }
  }
}

export function stopHeartbeatSweep(): void {
  if (sweepInterval) {
    clearInterval(sweepInterval);
    sweepInterval = null;
  }
}
