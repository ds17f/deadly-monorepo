import { describe, it, expect, beforeEach, vi } from "vitest";

vi.mock("../../db/userdata.js", () => ({
  getPlaybackPosition: vi.fn(() => null),
  upsertPlaybackPosition: vi.fn(),
}));

const {
  registerDevice,
  unregisterDevice,
  handleLoad,
  handleNext,
  handlePrev,
  handleSeek,
  getOrCreateState,
} = await import("../state.js");

function fakeSocket() {
  return { readyState: 1, OPEN: 1, send: vi.fn(), close: vi.fn() } as never;
}

const TRACKS = [
  { title: "Track 1", durationMs: 600_000 },
  { title: "Track 2", durationMs: 600_000 },
  { title: "Track 3", durationMs: 600_000 },
];

let seq = 0;
function freshUser() {
  seq += 1;
  return { userId: `hs-${seq}`, deviceId: `d-${seq}` };
}

describe("full-state handshake on register (ADR-0016 §3)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("adopts a reconnecting playing device's recording over the server's stale ownerless session", () => {
    // Server holds rec1 but has no owner (the reconnect-ghost / restart shape).
    const { userId, deviceId } = freshUser();
    registerDevice(userId, deviceId, "ios", "Phone", fakeSocket(), 3, "3.0.0");
    handleLoad(userId, deviceId, fakeSocket(), {
      showId: "s1", recordingId: "rec1", tracks: TRACKS,
      trackIndex: 0, positionMs: 0, durationMs: 600_000,
    });
    expect(getOrCreateState(userId).activeDeviceId).toBeNull();

    // The device reconnects, having switched to rec2 locally and still playing it.
    registerDevice(userId, deviceId, "ios", "Phone", fakeSocket(), 3, "3.0.0", {
      playing: true, showId: "s2", recordingId: "rec2", tracks: TRACKS,
      trackIndex: 2, positionMs: 42_000, durationMs: 600_000,
    });

    const s = getOrCreateState(userId);
    expect(s.recordingId).toBe("rec2"); // adopted the device's reality, not clobbered back to rec1
    expect(s.activeDeviceId).toBe(deviceId);
    expect(s.playing).toBe(true);
    expect(s.trackIndex).toBe(2);
    expect(s.positionMs).toBe(42_000);
  });

  it("never preempts a DIFFERENT live owner", () => {
    const { userId, deviceId: a } = freshUser();
    registerDevice(userId, a, "web", "Laptop", fakeSocket(), 3, "3.0.0");
    handleLoad(userId, a, fakeSocket(), {
      showId: "s1", recordingId: "rec1", tracks: TRACKS,
      trackIndex: 0, positionMs: 0, durationMs: 600_000, autoplay: true,
    });
    expect(getOrCreateState(userId).activeDeviceId).toBe(a);

    // B connects with a handshake claiming a different recording while playing.
    const b = `${a}-b`;
    registerDevice(userId, b, "ios", "Phone", fakeSocket(), 3, "3.0.0", {
      playing: true, showId: "s2", recordingId: "rec2", tracks: TRACKS,
      trackIndex: 1, positionMs: 9_999, durationMs: 600_000,
    });

    const s = getOrCreateState(userId);
    expect(s.activeDeviceId).toBe(a); // A still owns it
    expect(s.recordingId).toBe("rec1"); // not clobbered to rec2
  });

  it("does NOT adopt when the handshake device is not playing (parked)", () => {
    const { userId, deviceId } = freshUser();
    registerDevice(userId, deviceId, "ios", "Phone", fakeSocket(), 3, "3.0.0", {
      playing: false, showId: "s1", recordingId: "rec1", tracks: TRACKS,
      trackIndex: 0, positionMs: 0, durationMs: 600_000,
    });
    expect(getOrCreateState(userId).activeDeviceId).toBeNull();
  });

  it("ignores a handshake from a proto < 3 connection", () => {
    const { userId, deviceId } = freshUser();
    registerDevice(userId, deviceId, "ios", "Phone", fakeSocket(), 2, "2.0.0", {
      playing: true, showId: "s1", recordingId: "rec1", tracks: TRACKS,
      trackIndex: 0, positionMs: 0, durationMs: 600_000,
    });
    expect(getOrCreateState(userId).activeDeviceId).toBeNull();
  });
});

describe("unregisterDevice socket-identity guard (reconnect ghost fix)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("a stale close of a replaced socket does not unregister the newer one", () => {
    const { userId, deviceId } = freshUser();
    const socketA = fakeSocket();
    registerDevice(userId, deviceId, "ios", "Phone", socketA, 3, "3.0.0");
    handleLoad(userId, deviceId, socketA, {
      showId: "s1", recordingId: "rec1", tracks: TRACKS,
      trackIndex: 0, positionMs: 0, durationMs: 600_000, autoplay: true,
    });
    expect(getOrCreateState(userId).activeDeviceId).toBe(deviceId);

    // Reconnect: same deviceId on a new socket. liveDevices[key] now holds socketB.
    const socketB = fakeSocket();
    registerDevice(userId, deviceId, "ios", "Phone", socketB, 3, "3.0.0");

    // The OLD socket's delayed close fires now — must be a no-op.
    unregisterDevice(userId, deviceId, socketA);
    expect(getOrCreateState(userId).activeDeviceId).toBe(deviceId); // NOT ghosted

    // The real socket closing does deactivate.
    unregisterDevice(userId, deviceId, socketB);
    expect(getOrCreateState(userId).activeDeviceId).toBeNull();
  });
});

describe("trackNonce (ADR-0019 track-change intent)", () => {
  beforeEach(() => vi.clearAllMocks());

  function loadedSession() {
    const { userId, deviceId } = freshUser();
    const socket = fakeSocket();
    registerDevice(userId, deviceId, "web", "Web", socket, 3, "3.0.0");
    handleLoad(userId, deviceId, socket, {
      showId: "s1", recordingId: "rec1", tracks: TRACKS,
      trackIndex: 0, positionMs: 0, durationMs: 600_000, autoplay: true,
    });
    return { userId, deviceId };
  }

  it("bumps on next and prev, but not on load or seek", () => {
    const { userId } = loadedSession();
    expect(getOrCreateState(userId).trackNonce).toBe(0); // load did not bump

    handleNext(userId);
    expect(getOrCreateState(userId).trackNonce).toBe(1);

    handleSeek(userId, { trackIndex: 1, positionMs: 30_000 });
    expect(getOrCreateState(userId).trackNonce).toBe(1); // seek does not bump

    handlePrev(userId);
    expect(getOrCreateState(userId).trackNonce).toBe(2);
  });
});
