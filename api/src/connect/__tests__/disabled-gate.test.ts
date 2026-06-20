import { describe, it, expect, beforeEach } from "vitest";
import os from "node:os";
import path from "node:path";
import crypto from "node:crypto";

// Throwaway users-db so the app_settings flag persists to a real (temp) file.
process.env.USERS_DB_PATH = path.join(os.tmpdir(), `connect-gate-test-${crypto.randomUUID()}.db`);

const { getConnectEnabled, setConnectEnabled, getConnectMinProtocol, setConnectMinProtocol } = await import("../../db/appSettings.js");
const { connectDisabledCloseCode } = await import("../protocol.js");

describe("connect global kill switch", () => {
  beforeEach(() => {
    // Reset to the absent-row state by explicitly clearing isn't exposed, so
    // each assertion sets what it needs; default is checked on a fresh key below.
  });

  it("defaults to OFF when no row has ever been written", () => {
    // Fresh temp DB, key never set ⇒ code-side default.
    expect(getConnectEnabled()).toBe(false);
  });

  it("persists an explicit enable, then a disable", () => {
    setConnectEnabled(true);
    expect(getConnectEnabled()).toBe(true);
    setConnectEnabled(false);
    expect(getConnectEnabled()).toBe(false);
  });
});

describe("connect minimum-protocol gate", () => {
  it("defaults to 0 (allow all) when no row has ever been written", () => {
    expect(getConnectMinProtocol()).toBe(0);
  });

  it("persists an explicit floor, then a reset to 0", () => {
    setConnectMinProtocol(2);
    expect(getConnectMinProtocol()).toBe(2);
    setConnectMinProtocol(0);
    expect(getConnectMinProtocol()).toBe(0);
  });

  it("a floor of 2 admits proto >= 2 and excludes the proto-1 fleet", () => {
    setConnectMinProtocol(2);
    const min = getConnectMinProtocol();
    expect(1 < min).toBe(true); // proto-1 store fleet rejected
    expect(2 < min).toBe(false); // proto-2 beta build admitted
    setConnectMinProtocol(0);
  });
});

describe("connectDisabledCloseCode (backward compatibility)", () => {
  it("sends 4003 to legacy clients that only understand 4003 as terminal", () => {
    expect(connectDisabledCloseCode(0)).toBe(4003); // proto absent ⇒ legacy
    expect(connectDisabledCloseCode(1)).toBe(4003); // every shipped client today
  });

  it("sends 4005 to clients new enough to understand it", () => {
    expect(connectDisabledCloseCode(2)).toBe(4005);
    expect(connectDisabledCloseCode(3)).toBe(4005);
  });
});
