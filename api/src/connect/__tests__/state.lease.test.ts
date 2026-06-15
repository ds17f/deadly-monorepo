import { describe, it, expect, beforeEach, vi } from "vitest";

// state.ts persists position to SQLite; stub it out so these are pure
// in-memory state-machine tests.
vi.mock("../../db/userdata.js", () => ({
  getPlaybackPosition: vi.fn(() => null),
  upsertPlaybackPosition: vi.fn(),
}));

const {
  registerDevice,
  handleLoad,
  handleHeartbeat,
  getOrCreateState,
} = await import("../state.js");

// Minimal WebSocket stand-in: OPEN so broadcasts don't warn, send/close noop.
function fakeSocket() {
  return { readyState: 1, OPEN: 1, send: vi.fn(), close: vi.fn() } as never;
}

const TRACKS = [
  { title: "Track 1", durationMs: 600_000 },
  { title: "Track 2", durationMs: 600_000 },
];

let seq = 0;
// Module-level maps persist across tests; unique ids keep each test isolated.
function freshUser() {
  seq += 1;
  return { userId: `u-${seq}`, deviceId: `d-${seq}` };
}

// Register a device + load rec1 WITHOUT autoplay → session has a recording but
// activeDeviceId stays null. That's exactly the ownerless "ghost" the lease heals.
function ownerlessSession(proto = 1) {
  const { userId, deviceId } = freshUser();
  const socket = fakeSocket();
  registerDevice(userId, deviceId, "web", "Web", socket, proto, "1.0.0");
  handleLoad(userId, deviceId, socket, {
    showId: "s1",
    recordingId: "rec1",
    tracks: TRACKS,
    trackIndex: 0,
    positionMs: 0,
    durationMs: 600_000,
  });
  return { userId, deviceId, socket };
}

describe("handleHeartbeat ownership lease (ADR-0011 Chunk B)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("claims an ownerless session when the heartbeat is playing + recording matches, healing to the device's own track/pos", () => {
    const { userId, deviceId } = ownerlessSession(); // loaded at trackIndex 0
    expect(getOrCreateState(userId).activeDeviceId).toBeNull();

    // Device has since auto-advanced to track 1 — the server never learned that
    // (auto-advance emits no command), so its stored trackIndex is a stale 0.
    handleHeartbeat(userId, deviceId, { playing: true, recordingId: "rec1", trackIndex: 1, positionMs: 5_000 });

    const s = getOrCreateState(userId);
    expect(s.activeDeviceId).toBe(deviceId);
    expect(s.playing).toBe(true);
    expect(s.trackIndex).toBe(1); // healed to the device's real track, not the stale 0
    expect(s.positionMs).toBe(5_000); // position taken from the lease
  });

  it("does NOT claim when the lease is paused (parked/transferred-away device)", () => {
    const { userId, deviceId } = ownerlessSession();
    handleHeartbeat(userId, deviceId, { playing: false, recordingId: "rec1", trackIndex: 0, positionMs: 5_000 });
    expect(getOrCreateState(userId).activeDeviceId).toBeNull();
  });

  it("does NOT claim when the lease recording differs from the session", () => {
    const { userId, deviceId } = ownerlessSession();
    handleHeartbeat(userId, deviceId, { playing: true, recordingId: "OTHER", trackIndex: 0, positionMs: 5_000 });
    expect(getOrCreateState(userId).activeDeviceId).toBeNull();
  });

  it("ignores a legacy heartbeat with no lease payload", () => {
    const { userId, deviceId } = ownerlessSession();
    handleHeartbeat(userId, deviceId); // bare heartbeat
    expect(getOrCreateState(userId).activeDeviceId).toBeNull();
  });

  it("does NOT claim from a protocolVersion 0 (legacy) connection even with a lease", () => {
    const { userId, deviceId } = ownerlessSession(0);
    handleHeartbeat(userId, deviceId, { playing: true, recordingId: "rec1", trackIndex: 0, positionMs: 5_000 });
    expect(getOrCreateState(userId).activeDeviceId).toBeNull();
  });

  it("never preempts a device that already owns the session (claim-when-null only)", () => {
    const { userId, deviceId: a } = freshUser();
    const socketA = fakeSocket();
    registerDevice(userId, a, "web", "Web A", socketA, 1, "1.0.0");
    // A loads WITH autoplay → A is the active, playing owner.
    handleLoad(userId, a, socketA, {
      showId: "s1",
      recordingId: "rec1",
      tracks: TRACKS,
      trackIndex: 0,
      positionMs: 0,
      durationMs: 600_000,
      autoplay: true,
    });
    expect(getOrCreateState(userId).activeDeviceId).toBe(a);

    // B connects and leases the same recording while playing — must back off.
    const b = `${a}-b`;
    registerDevice(userId, b, "ios", "Phone B", fakeSocket(), 1, "1.0.0");
    handleHeartbeat(userId, b, { playing: true, recordingId: "rec1", trackIndex: 0, positionMs: 9_999 });

    const s = getOrCreateState(userId);
    expect(s.activeDeviceId).toBe(a); // unchanged — never preempted
    expect(s.positionMs).not.toBe(9_999);
  });

  it("refreshing as the existing owner leaves ownership intact", () => {
    const { userId, deviceId } = ownerlessSession();
    // First heartbeat claims the vacuum.
    handleHeartbeat(userId, deviceId, { playing: true, recordingId: "rec1", trackIndex: 0, positionMs: 5_000 });
    const versionAfterClaim = getOrCreateState(userId).version;
    // Owner re-heartbeats → no ownership change, no extra broadcast/mutate.
    handleHeartbeat(userId, deviceId, { playing: true, recordingId: "rec1", trackIndex: 0, positionMs: 6_000 });
    const s = getOrCreateState(userId);
    expect(s.activeDeviceId).toBe(deviceId);
    expect(s.version).toBe(versionAfterClaim); // refresh didn't mutate/broadcast
  });
});
