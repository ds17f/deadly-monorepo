"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { ConnectContext } from "@/contexts/ConnectContext";
import type { ConnectDevice, ConnectState } from "@/types/connect";

const HEARTBEAT_INTERVAL_MS = 15_000;
const RECONNECT_DELAYS_MS = [1_000, 2_000, 4_000, 8_000, 30_000];
const DEVICE_ID_KEY = "deadly-device-id";

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

async function fetchToken(): Promise<string | null> {
  try {
    const res = await fetch("/api/auth/token", { credentials: "include" });
    if (!res.ok) return null;
    const data = await res.json() as { token?: string };
    return data.token ?? null;
  } catch {
    return null;
  }
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

  const wsRef = useRef<WebSocket | null>(null);
  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectAttemptRef = useRef(0);
  const shouldConnectRef = useRef(false);

  const clearHeartbeat = useCallback(() => {
    if (heartbeatRef.current) {
      clearInterval(heartbeatRef.current);
      heartbeatRef.current = null;
    }
  }, []);

  const clearReconnectTimer = useCallback(() => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
  }, []);

  const connect = useCallback(async () => {
    if (!shouldConnectRef.current) return;

    const token = await fetchToken();
    if (!token || !shouldConnectRef.current) return;

    const deviceId = getOrCreateDeviceId();
    const deviceName = getDeviceName();
    setMyDeviceId(deviceId);

    const wsProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = `${wsProtocol}//${window.location.host}/ws/connect?token=${encodeURIComponent(token)}`;

    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => {
      if (!shouldConnectRef.current) {
        ws.close();
        return;
      }
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
    };

    ws.onmessage = (event: MessageEvent<string>) => {
      let msg: { type: string; [key: string]: unknown };
      try {
        msg = JSON.parse(event.data);
      } catch {
        return;
      }

      if (msg.type === "state") {
        setConnectState(msg.state as ConnectState);
      } else if (msg.type === "devices") {
        setDevices(msg.devices as ConnectDevice[]);
      }
    };

    ws.onclose = (event) => {
      clearHeartbeat();
      setConnected(false);
      wsRef.current = null;

      // 4003 = Unauthorized (terminal), 4001 = heartbeat timeout (reconnect)
      if (!shouldConnectRef.current || event.code === 4003) return;

      const delay = RECONNECT_DELAYS_MS[
        Math.min(reconnectAttemptRef.current, RECONNECT_DELAYS_MS.length - 1)
      ];
      reconnectAttemptRef.current += 1;
      reconnectTimerRef.current = setTimeout(connect, delay);
    };

    ws.onerror = () => {
      // onclose fires after onerror; reconnect logic is there
    };
  }, [clearHeartbeat]);

  const sendCommand = useCallback((action: string, extra?: Record<string, unknown>) => {
    const ws = wsRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: "command", action, ...extra }));
    }
  }, []);

  const disconnect = useCallback(() => {
    shouldConnectRef.current = false;
    clearReconnectTimer();
    clearHeartbeat();
    const ws = wsRef.current;
    if (ws) {
      wsRef.current = null;
      ws.close();
    }
    setConnected(false);
    setDevices([]);
    setConnectState(null);
  }, [clearHeartbeat, clearReconnectTimer]);

  useEffect(() => {
    if (isLoading) return;

    if (user) {
      shouldConnectRef.current = true;
      reconnectAttemptRef.current = 0;
      connect();
    } else {
      disconnect();
    }

    return () => {
      disconnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, isLoading]);

  return (
    <ConnectContext.Provider value={{ devices, state: connectState, myDeviceId, connected, sendCommand }}>
      {children}
    </ConnectContext.Provider>
  );
}
