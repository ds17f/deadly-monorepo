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
  handleSeek,
  handlePosition,
  getOrCreateState,
} = await import("../state.js");

// A session whose shared durationMs is 0 — exactly the hydrated/restored case where
// a controller's `fraction * durationMs` collapses to 0 and every drag seeks to start.
function zeroDurationSession() {
  const { userId, deviceId } = freshUser();
  const socket = fakeSocket();
  registerDevice(userId, deviceId, "web", "Web", socket, 1, "1.0.0");
  handleLoad(userId, deviceId, socket, {
    showId: "s1",
    recordingId: "rec1",
    tracks: TRACKS,
    trackIndex: 0,
    positionMs: 0,
    durationMs: 0, // hydrated/restored: no duration
    autoplay: true,
  });
  return { userId, deviceId };
}

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
  return { userId: `seek-u-${seq}`, deviceId: `seek-d-${seq}` };
}

// An ACTIVE, playing session: load WITH autoplay so the device owns the session
// (activeDeviceId === deviceId), which is what handlePosition requires.
function activeSession() {
  const { userId, deviceId } = freshUser();
  const socket = fakeSocket();
  registerDevice(userId, deviceId, "web", "Web", socket, 1, "1.0.0");
  handleLoad(userId, deviceId, socket, {
    showId: "s1",
    recordingId: "rec1",
    tracks: TRACKS,
    trackIndex: 0,
    positionMs: 0,
    durationMs: 600_000,
    autoplay: true,
  });
  return { userId, deviceId, socket };
}

// ADR-0017: an explicit seek is INTENT (bump seekNonce); a routine position report
// is NOT (leave it). The active device follows a remote seek iff seekNonce advanced,
// so a self-echoed position report can never masquerade as a seek (the "skips" bug).
describe("seek intent vs position report (ADR-0017)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("starts at seekNonce 0", () => {
    const { userId } = activeSession();
    expect(getOrCreateState(userId).seekNonce).toBe(0);
  });

  it("handleSeek bumps seekNonce by exactly 1 and applies the position", () => {
    const { userId } = activeSession();
    handleSeek(userId, { trackIndex: 0, positionMs: 123_000 });
    const s = getOrCreateState(userId);
    expect(s.seekNonce).toBe(1);
    expect(s.positionMs).toBe(123_000);
  });

  it("handlePosition does NOT bump seekNonce (the anti-skip guarantee)", () => {
    const { userId, deviceId } = activeSession();
    handleSeek(userId, { trackIndex: 0, positionMs: 100_000 }); // nonce -> 1
    expect(getOrCreateState(userId).seekNonce).toBe(1);

    // The active device's own ~5s position reports must leave the nonce alone, even
    // as positionMs moves — otherwise an echo would read as a remote seek.
    handlePosition(userId, deviceId, 105_000);
    handlePosition(userId, deviceId, 110_000);
    const s = getOrCreateState(userId);
    expect(s.seekNonce).toBe(1); // unchanged across position reports
    expect(s.positionMs).toBe(110_000); // position still tracked
  });

  it("monotonically increments across interleaved seeks and reports", () => {
    const { userId, deviceId } = activeSession();
    handleSeek(userId, { trackIndex: 0, positionMs: 10_000 }); // 1
    handlePosition(userId, deviceId, 15_000); // still 1
    handleSeek(userId, { trackIndex: 0, positionMs: 12_000 }); // 2 — a tiny backward seek still counts
    handlePosition(userId, deviceId, 20_000); // still 2
    handleSeek(userId, { trackIndex: 1, positionMs: 0 }); // 3
    expect(getOrCreateState(userId).seekNonce).toBe(3);
  });
});

// ADR-0017 follow-up: the active device propagates its real durationMs in position
// reports so controllers of a hydrated (durationMs=0) session get a valid scrubber
// scale — otherwise `fraction * 0 = 0` and every remote drag seeks to track start.
describe("durationMs propagation via position reports (ADR-0017)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("a position report with durationMs>0 fills in a zero shared duration", () => {
    const { userId, deviceId } = zeroDurationSession();
    expect(getOrCreateState(userId).durationMs).toBe(0);
    handlePosition(userId, deviceId, 5_000, 600_000);
    expect(getOrCreateState(userId).durationMs).toBe(600_000);
  });

  it("does not bump seekNonce while carrying duration (still just a report)", () => {
    const { userId, deviceId } = zeroDurationSession();
    handlePosition(userId, deviceId, 5_000, 600_000);
    expect(getOrCreateState(userId).seekNonce).toBe(0);
  });

  it("ignores a 0/unknown duration (never clobbers a real one back to 0)", () => {
    const { userId, deviceId } = activeSession(); // loaded with durationMs 600_000
    expect(getOrCreateState(userId).durationMs).toBe(600_000);
    handlePosition(userId, deviceId, 5_000, 0);
    expect(getOrCreateState(userId).durationMs).toBe(600_000);
  });

  it("a legacy position report with no duration leaves duration untouched", () => {
    const { userId, deviceId } = activeSession();
    handlePosition(userId, deviceId, 5_000); // no durationMs arg
    expect(getOrCreateState(userId).durationMs).toBe(600_000);
  });
});
