"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { ConnectContext } from "@/contexts/ConnectContext";
import type { ConnectDevice, ConnectState } from "@/types/connect";

const HEARTBEAT_INTERVAL_MS = 15_000;
const RECONNECT_DELAYS_MS = [1_000, 2_000, 4_000, 8_000, 30_000];
const DEVICE_ID_KEY = "deadly-device-id";
const TIME_SYNC_REFRESH_MS = 5 * 60 * 1000;
const TIME_SYNC_SAMPLES = 3;
const TIME_SYNC_SAMPLE_SPACING_MS = 200;

const log = (...args: unknown[]) => console.log("[Connect]", ...args);
const warn = (...args: unknown[]) => console.warn("[Connect]", ...args);

function getOrCreateDeviceId(): string {
  let id = localStorage.getItem(DEVICE_ID_KEY);
  if (!id) {
    id = crypto.randomUUID();
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
  const [devices, setDevices] = useState<ConnectDevice[]>([]);
  const [connectState, setConnectState] = useState<ConnectState | null>(null);
  const [myDeviceId, setMyDeviceId] = useState<string | null>(null);
  const [connected, setConnected] = useState(false);

  const [activeDeviceVolume, setActiveDeviceVolume] = useState<number | null>(null);
  const [serverTimeOffsetMs, setServerTimeOffsetMs] = useState(0);

  const wsRef = useRef<WebSocket | null>(null);
  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const timeSyncRefreshRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectAttemptRef = useRef(0);
  const shouldConnectRef = useRef(false);
  const currentVersionRef = useRef(0);
  const volumeListenersRef = useRef<Array<(volume: number) => void>>([]);
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
        ws.close();
        return;
      }
      log("Connected, sending register");
      setConnected(true);
      reconnectAttemptRef.current = 0;

      ws.send(JSON.stringify({
        type: "register",
        deviceId,
        deviceType: "web",
        deviceName,
      }));

      clearHeartbeat();
      heartbeatRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: "heartbeat" }));
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
      log(`Disconnected: code=${event.code} reason=${event.reason || "(none)"}`);
      clearHeartbeat();
      clearTimeSyncRefresh();
      setServerTimeOffsetMs(0);
      timeSyncBestRttRef.current = Number.POSITIVE_INFINITY;
      setConnected(false);
      wsRef.current = null;

      // 4003 = Unauthorized (terminal), 4001 = heartbeat timeout (reconnect)
      if (!shouldConnectRef.current || event.code === 4003) {
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

  useEffect(() => {
    if (isLoading) return;

    if (user) {
      log(`User authenticated, starting connect`);
      shouldConnectRef.current = true;
      reconnectAttemptRef.current = 0;
      connect();
    } else {
      log(`No user, disconnecting`);
      disconnect();
    }

    return () => {
      disconnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, isLoading]);

  return (
    <ConnectContext.Provider value={{ devices, state: connectState, myDeviceId, connected, sendCommand, activeDeviceVolume, onVolumeMessage, reportVolume, serverTimeOffsetMs }}>
      {children}
    </ConnectContext.Provider>
  );
}
