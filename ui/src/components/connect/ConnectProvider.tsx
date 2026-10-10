"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { usePathname } from "next/navigation";
import { useAuth } from "@/contexts/AuthContext";
import { ConnectContext } from "@/contexts/ConnectContext";
import type { LocalPlaybackSnapshot, LocalHandshakeSnapshot } from "@/contexts/ConnectContext";
import type { ConnectDevice, ConnectState } from "@/types/connect";
import { randomUUID } from "@/lib/uuid";

// Connect WS wire-contract version. See docs/PROTOCOL.md for semantics.
// Bump in lockstep with the documented protocol; the server may branch on it.
// v2: understands the 4005 "Connect disabled" terminal close code.
// v3: full-state handshake on register (ADR-0016 §3) + honors trackNonce
//     (ADR-0019); heartbeat lease now carries trackIndex.
const CONNECT_PROTOCOL_VERSION = 3;
// Build identity for telemetry only — never branched on. Mirrors analytics.ts.
const APP_VERSION = (process.env.NEXT_PUBLIC_DATA_VERSION ?? "web").slice(0, 20);

const HEARTBEAT_INTERVAL_MS = 15_000;
const RECONNECT_DELAYS_MS = [1_000, 2_000, 4_000, 8_000, 30_000];
const DEVICE_ID_KEY = "deadly-device-id";
// Per-web-install Connect opt-in (ADR-0018). A user may run several browsers/
// installs, each a distinct Connect device, so this is local — NOT an account
// setting. Default OFF: a fresh install must explicitly opt into the beta.
const OPTED_IN_KEY = "deadly-connect-opted-in";
// Connect close codes the client must NOT reconnect after. 4003 Unauthorized,
// 4000 replaced-by-newer-connection, 4005 Connect globally disabled (server
// kill switch). The browser surfaces server-initiated close codes to onclose
// directly, so unlike Android no special handshake handling is needed.
const TERMINAL_CLOSE_CODES = new Set([4000, 4003, 4005]);
const TIME_SYNC_REFRESH_MS = 5 * 60 * 1000;
const TIME_SYNC_SAMPLES = 3;
const TIME_SYNC_SAMPLE_SPACING_MS = 200;

const log = (...args: unknown[]) => console.log("[Connect]", ...args);
const warn = (...args: unknown[]) => console.warn("[Connect]", ...args);

function getOrCreateDeviceId(): string {
  let id = localStorage.getItem(DEVICE_ID_KEY);
  if (!id) {
    id = randomUUID();
    localStorage.setItem(DEVICE_ID_KEY, id);
  }
  return id;
}

function getDeviceName(): string {
  const ua = navigator.userAgent;
  let browser = "Browser";
  let os = "";

  if (ua.includes("Edg/")) browser = "Edge";
  else if (ua.includes("Chrome/")) browser = "Chrome";
  else if (ua.includes("Firefox/")) browser = "Firefox";
  else if (ua.includes("Safari/")) browser = "Safari";

  if (ua.includes("Mac OS X")) os = " on Mac";
  else if (ua.includes("Windows")) os = " on Windows";
  else if (ua.includes("Linux")) os = " on Linux";

  return `${browser}${os}`;
}

export default function ConnectProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, isLoading } = useAuth();
  // Admin pages are a back-office dashboard, not a playback surface — keep the
  // Connect WS (heartbeat, ownership lease, reconnect loop) out of them so an
  // admin session never registers as a device. Pathname-gated, so navigating
  // in tears the socket down and navigating back out re-opens it.
  const pathname = usePathname();
  const onAdmin = pathname?.startsWith("/admin") ?? false;
  const [devices, setDevices] = useState<ConnectDevice[]>([]);
  const [connectState, setConnectState] = useState<ConnectState | null>(null);
  const [myDeviceId, setMyDeviceId] = useState<string | null>(null);
  const [connected, setConnected] = useState(false);

  const [activeDeviceVolume, setActiveDeviceVolume] = useState<number | null>(null);
  const [serverTimeOffsetMs, setServerTimeOffsetMs] = useState(0);

  // ADR-0018: server global flag (greys/hides the Connect UI) + per-install
  // beta opt-in. Both must be true to attempt the socket.
  const [serverConnectEnabled, setServerConnectEnabled] = useState(false);
  const [connectOptedIn, setConnectOptedInState] = useState(false);

  const wsRef = useRef<WebSocket | null>(null);
  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const timeSyncRefreshRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectAttemptRef = useRef(0);
  const shouldConnectRef = useRef(false);
  const currentVersionRef = useRef(0);
  const volumeListenersRef = useRef<Array<(volume: number) => void>>([]);
  // ADR-0011 Chunk B: getter for this device's local playback, registered by the
  // player. The heartbeat calls it to renew the ownership lease. Default: no
  // source loaded ⇒ plain heartbeat.
  const localPlaybackRef = useRef<() => LocalPlaybackSnapshot | null>(() => null);
  // ADR-0016 §3: getter for this tab's full-state handshake, read once at register
  // time. Default: nothing to declare ⇒ plain register.
  const localHandshakeRef = useRef<() => LocalHandshakeSnapshot | null>(() => null);
  // Tracks the best (lowest-RTT) sample within the current sync batch so we
  // can keep updating as better samples arrive but ignore worse ones.
  const timeSyncBestRttRef = useRef<number>(Number.POSITIVE_INFINITY);

  const clearHeartbeat = useCallback(() => {
    if (heartbeatRef.current) {
      clearInterval(heartbeatRef.current);
      heartbeatRef.current = null;
    }
  }, []);

  const clearTimeSyncRefresh = useCallback(() => {
    if (timeSyncRefreshRef.current) {
      clearInterval(timeSyncRefreshRef.current);
      timeSyncRefreshRef.current = null;
    }
  }, []);

  const runTimeSync = useCallback(() => {
    const ws = wsRef.current;
    if (!ws || ws.readyState !== WebSocket.OPEN) return;
    // New batch: reset best-sample tracking so a fresh best can win over
    // any sample from the previous batch.
    timeSyncBestRttRef.current = Number.POSITIVE_INFINITY;
    for (let i = 0; i < TIME_SYNC_SAMPLES; i++) {
      setTimeout(() => {
        const sock = wsRef.current;
        if (!sock || sock.readyState !== WebSocket.OPEN) return;
        sock.send(JSON.stringify({ type: "time_sync", clientTs: Date.now() }));
      }, i * TIME_SYNC_SAMPLE_SPACING_MS);
    }
  }, []);

  const clearReconnectTimer = useCallback(() => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
  }, []);

  const connect = useCallback(() => {
    if (!shouldConnectRef.current) return;

    // Re-entrancy guard: a reconnect timer and the auth effect (or a second
    // trigger) can both call connect() before the in-flight socket settles,
    // leaving two sockets for the same deviceId. The server then closes the
    // older one with 4000 "replaced by new connection" — whose onclose
    // reconnects and replaces again, a self-perpetuating churn. Bail if a
    // socket is already opening or open; onclose nulls wsRef before reconnecting.
    const existing = wsRef.current;
    if (existing && (existing.readyState === WebSocket.CONNECTING || existing.readyState === WebSocket.OPEN)) {
      log("connect: socket already opening/open, skipping");
      return;
    }

    const deviceId = getOrCreateDeviceId();
    const deviceName = getDeviceName();
    setMyDeviceId(deviceId);

    const wsProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = `${wsProtocol}//${window.location.host}/ws/connect`;

    log(`Connecting to ${wsUrl} as ${deviceName} (${deviceId})`);
    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => {
      if (!shouldConnectRef.current) {
        // Null the ref we're closing so this socket's late onclose (guarded on
        // wsRef identity) doesn't touch a socket that superseded it.
        if (wsRef.current === ws) wsRef.current = null;
        ws.close();
        return;
      }
      log("Connected, sending register");
      setConnected(true);
      reconnectAttemptRef.current = 0;
      // Reset the version watermark on every (re)connect. The monotonic guard
      // only dedupes out-of-order messages WITHIN one connection; across a
      // reconnect the server may have restarted and reset its counter to 0, so
      // the first snapshot it sends here is authoritative regardless of version.
      // Without this, a tab that held a high version pre-restart silently
      // discards every fresh state (and never re-asserts its tracklist).
      currentVersionRef.current = -1;

      // ADR-0016 §3: if this tab holds live audio, declare it so the server's
      // FIRST post-register broadcast reflects our reality (closes the reconnect
      // stale-snapshot clobber). Only when actually playing a recording — a plain
      // register otherwise. The server ignores a non-playing handshake anyway.
      const handshake = localHandshakeRef.current();
      const includeHandshake = handshake != null && handshake.playing && !!handshake.recordingId;
      ws.send(JSON.stringify({
        type: "register",
        deviceId,
        deviceType: "web",
        deviceName,
        // ADR-0011 §3 / docs/PROTOCOL.md: wire-contract version the server may
        // branch on; appVersion is build identity for telemetry only.
        protocolVersion: CONNECT_PROTOCOL_VERSION,
        appVersion: APP_VERSION,
        ...(includeHandshake ? { handshake } : {}),
      }));

      clearHeartbeat();
      heartbeatRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          // ADR-0011 Chunk B: piggyback the ownership-lease renewal when audio is
          // loaded locally so the server can heal an ownerless session.
          const lease = localPlaybackRef.current();
          ws.send(JSON.stringify(lease ? { type: "heartbeat", ...lease } : { type: "heartbeat" }));
        }
      }, HEARTBEAT_INTERVAL_MS);

      clearTimeSyncRefresh();
      runTimeSync();
      timeSyncRefreshRef.current = setInterval(runTimeSync, TIME_SYNC_REFRESH_MS);
    };

    ws.onmessage = (event: MessageEvent<string>) => {
      let msg: { type: string; [key: string]: unknown };
      try {
        msg = JSON.parse(event.data);
      } catch {
        return;
      }

      if (msg.type === "state") {
        const newState = msg.state as ConnectState;
        if (newState.version <= currentVersionRef.current) {
          log(`Ignoring stale state v${newState.version} (current=${currentVersionRef.current})`);
          return;
        }
        currentVersionRef.current = newState.version;
        const isActive = newState.activeDeviceId === deviceId;
        log(
          `State v${newState.version}: show=${newState.showId ?? "none"} rec=${newState.recordingId ?? "none"} ` +
          `track=${newState.trackIndex} playing=${newState.playing} ` +
          `activeDevice=${newState.activeDeviceId ?? "none"} isMe=${isActive}`
        );
        setConnectState(newState);
      } else if (msg.type === "devices") {
        const devs = msg.devices as ConnectDevice[];
        log(`Devices (${devs.length}): ${devs.map(d => `${d.deviceName}[${d.deviceType}]`).join(", ")}`);
        setDevices(devs);
      } else if (msg.type === "volume") {
        const volume = msg.volume as number;
        log(`Volume command: ${volume}`);
        volumeListenersRef.current.forEach(fn => fn(volume));
        setActiveDeviceVolume(volume);
      } else if (msg.type === "volume_report") {
        const volume = msg.volume as number;
        log(`Volume report from ${msg.deviceId}: ${volume}`);
        setActiveDeviceVolume(volume);
      } else if (msg.type === "time_sync") {
        const clientTs = msg.clientTs as number;
        const serverTs = msg.serverTs as number;
        const now = Date.now();
        const rtt = now - clientTs;
        // NTP-style: assume symmetric one-way delay = rtt/2.
        // serverTimeOffset = serverNow_at_send - clientNow_at_send.
        // serverNow_at_send ≈ serverTs (server stamps at send time).
        // clientNow_at_send ≈ clientTs + rtt/2 (midway through round-trip).
        const offset = serverTs - (clientTs + rtt / 2);
        if (rtt < timeSyncBestRttRef.current) {
          timeSyncBestRttRef.current = rtt;
          setServerTimeOffsetMs(offset);
          log(`time_sync: rtt=${rtt}ms offset=${offset}ms (kept)`);
        } else {
          log(`time_sync: rtt=${rtt}ms offset=${offset}ms (dropped, best=${timeSyncBestRttRef.current}ms)`);
        }
      }
    };

    ws.onclose = (event) => {
      // Socket-identity guard: a superseded socket's late close must not tear down
      // the fresh socket that replaced it (nulling wsRef / killing its heartbeat
      // would orphan the live connection). Only the socket wsRef still holds may
      // run this teardown; a stale one bails.
      if (wsRef.current !== ws) {
        log(`Ignoring close for a superseded socket: code=${event.code}`);
        return;
      }
      log(`Disconnected: code=${event.code} reason=${event.reason || "(none)"}`);
      clearHeartbeat();
      clearTimeSyncRefresh();
      setServerTimeOffsetMs(0);
      timeSyncBestRttRef.current = Number.POSITIVE_INFINITY;
      setConnected(false);
      wsRef.current = null;

      // The server refused us because Connect is globally disabled — flip the
      // flag so the Connect UI greys out immediately (ADR-0018 runtime path).
      if (event.code === 4005) setServerConnectEnabled(false);

      // Terminal codes (don't reconnect): 4003 Unauthorized, 4000 replaced-by-
      // newer-connection (reconnecting would kick the rightful newer socket and
      // self-perpetuate churn), 4005 Connect globally disabled by the server.
      // 4001 (heartbeat timeout) and clean drops fall through to reconnect.
      if (!shouldConnectRef.current || TERMINAL_CLOSE_CODES.has(event.code)) {
        log(`Not reconnecting: shouldConnect=${shouldConnectRef.current} code=${event.code}`);
        return;
      }

      const delay = RECONNECT_DELAYS_MS[
        Math.min(reconnectAttemptRef.current, RECONNECT_DELAYS_MS.length - 1)
      ];
      reconnectAttemptRef.current += 1;
      log(`Reconnecting in ${delay}ms (attempt ${reconnectAttemptRef.current})`);
      reconnectTimerRef.current = setTimeout(() => connect(), delay);
    };

    ws.onerror = () => {
      warn("WebSocket error (onclose will handle reconnect)");
    };
  }, [clearHeartbeat, clearTimeSyncRefresh, runTimeSync]);

  const onVolumeMessage = useCallback((handler: (volume: number) => void) => {
    volumeListenersRef.current.push(handler);
    return () => {
      volumeListenersRef.current = volumeListenersRef.current.filter(fn => fn !== handler);
    };
  }, []);

  const reportVolume = useCallback((volume: number) => {
    setActiveDeviceVolume(volume);
    const ws = wsRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      log(`reportVolume: ${volume}`);
      ws.send(JSON.stringify({ type: "command", action: "volume_report", volume }));
    }
  }, []);

  const sendCommand = useCallback((action: string, extra?: Record<string, unknown>) => {
    const ws = wsRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      log(`sendCommand: ${action}`, extra ? JSON.stringify(extra).slice(0, 200) : "");
      ws.send(JSON.stringify({ type: "command", action, ...extra }));
    } else {
      warn(`sendCommand: ${action} DROPPED — ws not open (readyState=${ws?.readyState})`);
    }
  }, []);

  // ADR-0018: best-effort read of the global Connect flag. Short, no-store, and
  // failures keep the current value. Called on mount and on focus/visibility so
  // an admin flip is reflected without a reload.
  const refreshServerConnectEnabled = useCallback(async () => {
    try {
      const res = await fetch("/api/connect/enabled", { cache: "no-store" });
      if (!res.ok) return;
      const json = (await res.json()) as { connectEnabled?: unknown };
      if (typeof json.connectEnabled === "boolean") setServerConnectEnabled(json.connectEnabled);
    } catch {
      // keep the current value
    }
  }, []);

  const setConnectOptedIn = useCallback((value: boolean) => {
    try {
      localStorage.setItem(OPTED_IN_KEY, value ? "1" : "0");
    } catch {
      // ignore storage failures (private mode); state still updates
    }
    setConnectOptedInState(value);
  }, []);

  const setLocalPlaybackSource = useCallback(
    (source: (() => LocalPlaybackSnapshot | null) | null) => {
      localPlaybackRef.current = source ?? (() => null);
    },
    [],
  );

  const setLocalHandshakeSource = useCallback(
    (source: (() => LocalHandshakeSnapshot | null) | null) => {
      localHandshakeRef.current = source ?? (() => null);
    },
    [],
  );

  const disconnect = useCallback(() => {
    log("disconnect() called");
    shouldConnectRef.current = false;
    clearReconnectTimer();
    clearHeartbeat();
    clearTimeSyncRefresh();
    const ws = wsRef.current;
    if (ws) {
      wsRef.current = null;
      ws.close();
    }
    setConnected(false);
    setDevices([]);
    setConnectState(null);
    setActiveDeviceVolume(null);
    setServerTimeOffsetMs(0);
    timeSyncBestRttRef.current = Number.POSITIVE_INFINITY;
    currentVersionRef.current = 0;
  }, [clearHeartbeat, clearReconnectTimer, clearTimeSyncRefresh]);


  // Seed the per-install opt-in from localStorage, then refresh the server flag
  // on mount and whenever the tab regains focus/visibility (ADR-0018).
  useEffect(() => {
    try {
      setConnectOptedInState(localStorage.getItem(OPTED_IN_KEY) === "1");
    } catch {
      // ignore
    }
    void refreshServerConnectEnabled();
    const onFocus = () => void refreshServerConnectEnabled();
    const onVisible = () => {
      if (document.visibilityState === "visible") void refreshServerConnectEnabled();
    };
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [refreshServerConnectEnabled]);

  useEffect(() => {
    if (isLoading) return;

    // ADR-0018: attempt the socket only when signed in, opted into the beta on
    // this install, AND the server kill switch is on. Any of these going false
    // re-runs the effect and tears the socket down — so an admin disabling
    // Connect (flag refresh flips serverConnectEnabled) disconnects every tab.
    if (user && !onAdmin && connectOptedIn && serverConnectEnabled) {
      log(`User authenticated + opted in + server enabled, starting connect`);
      shouldConnectRef.current = true;
      reconnectAttemptRef.current = 0;
      connect();
    } else {
      log(`Not connecting (user=${!!user} admin=${onAdmin} optedIn=${connectOptedIn} serverEnabled=${serverConnectEnabled})`);
      disconnect();
    }

    return () => {
      disconnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, isLoading, onAdmin, connectOptedIn, serverConnectEnabled]);

  return (
    <ConnectContext.Provider value={{ devices, state: connectState, myDeviceId, connected, sendCommand, activeDeviceVolume, onVolumeMessage, reportVolume, setLocalPlaybackSource, setLocalHandshakeSource, serverTimeOffsetMs, serverConnectEnabled, refreshServerConnectEnabled, connectOptedIn, setConnectOptedIn }}>
      {children}
    </ConnectContext.Provider>
  );
}
