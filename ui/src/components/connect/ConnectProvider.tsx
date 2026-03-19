"use client";

import { useState, useEffect, useRef, useCallback, useMemo } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { ConnectContext } from "@/contexts/ConnectContext";
import type { ConnectDevice, PlaybackState, ActiveSession } from "@/contexts/ConnectContext";
import { ConnectWebSocket } from "@/lib/connectWs";

function generateDeviceId(): string {
  const stored = typeof window !== "undefined" ? localStorage.getItem("deadly_device_id") : null;
  if (stored) return stored;
  const id = crypto.randomUUID();
  if (typeof window !== "undefined") localStorage.setItem("deadly_device_id", id);
  return id;
}

function getDeviceName(): string {
  if (typeof navigator === "undefined") return "Web Browser";
  const ua = navigator.userAgent;
  let browser = "Browser";
  if (ua.includes("Firefox/")) browser = "Firefox";
  else if (ua.includes("Edg/")) browser = "Edge";
  else if (ua.includes("Chrome/")) browser = "Chrome";
  else if (ua.includes("Safari/") && !ua.includes("Chrome")) browser = "Safari";

  // Short session suffix from deviceId to distinguish multiple tabs/windows
  const deviceId = generateDeviceId();
  const suffix = deviceId.slice(0, 4);
  return `${browser} · ${suffix}`;
}

function getWsUrl(): string {
  if (typeof window === "undefined") return "";
  const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${proto}//${window.location.host}/ws/connect`;
}

export default function ConnectProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [isConnected, setIsConnected] = useState(false);
  const [devices, setDevices] = useState<ConnectDevice[]>([]);
  const [activeSession, setActiveSession] = useState<ActiveSession | null>(null);
  // Legacy state — kept for backward compatibility
  const [incomingState, setIncomingState] = useState<PlaybackState | null>(null);
  const [playingOnDevice, setPlayingOnDevice] = useState<ConnectDevice | null>(null);
  const wsRef = useRef<ConnectWebSocket | null>(null);

  const localDeviceId = useMemo(() => {
    if (typeof window === "undefined") return "";
    return generateDeviceId();
  }, []);

  const isActiveDevice = activeSession?.deviceId === localDeviceId && localDeviceId !== "";

  useEffect(() => {
    if (!user?.id) {
      wsRef.current?.close();
      wsRef.current = null;
      setIsConnected(false);
      setDevices([]);
      setActiveSession(null);
      return;
    }

    const deviceId = generateDeviceId();
    const ws = new ConnectWebSocket({
      url: getWsUrl(),
      onMessage: (data: unknown) => {
        const msg = data as {
          type: string;
          devices?: ConnectDevice[];
          state?: PlaybackState;
          session?: ActiveSession | null;
          fromDeviceId?: string;
          command?: { action: string };
        };
        switch (msg.type) {
          case "devices":
            setDevices(msg.devices ?? []);
            break;
          case "active_session":
            setActiveSession(msg.session ?? null);
            break;
          case "transfer_received":
            if (msg.state) {
              setIncomingState(msg.state);
              setPlayingOnDevice(null);
            }
            break;
          case "command_received":
            if (msg.command) {
              window.dispatchEvent(new CustomEvent("connect:command", { detail: msg.command }));
            }
            break;
          case "position_update":
            break;
        }
      },
      onOpen: () => {
        setIsConnected(true);
        ws.send({
          type: "register",
          device: {
            deviceId,
            type: "web",
            name: getDeviceName(),
            capabilities: ["playback", "control"],
          },
        });
      },
      onClose: () => {
        setIsConnected(false);
      },
    });

    ws.connect();
    wsRef.current = ws;

    return () => {
      ws.close();
      wsRef.current = null;
    };
  }, [user?.id]);

  const announcePlayback = useCallback((state: PlaybackState) => {
    wsRef.current?.send({ type: "session_update", state });
  }, []);

  const claimSession = useCallback(() => {
    wsRef.current?.send({ type: "session_claim" });
  }, []);

  const playOnDevice = useCallback((deviceId: string, state: PlaybackState) => {
    wsRef.current?.send({ type: "session_play_on", targetDeviceId: deviceId, state });
  }, []);

  const sendPositionUpdate = useCallback((state: PlaybackState) => {
    wsRef.current?.send({ type: "position_update", state });
  }, []);

  // Legacy functions
  const transferPlayback = useCallback((targetDeviceId: string, state: PlaybackState) => {
    wsRef.current?.send({ type: "transfer", targetDeviceId, state });
    const target = devices.find((d) => d.deviceId === targetDeviceId) ?? null;
    setPlayingOnDevice(target);
  }, [devices]);

  const sendCommand = useCallback((targetDeviceId: string, action: string, seekMs?: number) => {
    wsRef.current?.send({
      type: "command",
      targetDeviceId,
      command: { action, ...(seekMs !== undefined ? { seekMs } : {}) },
    });
  }, []);

  const clearIncomingState = useCallback(() => {
    setIncomingState(null);
  }, []);

  const value = useMemo(() => ({
    isConnected,
    devices,
    activeSession,
    isActiveDevice,
    announcePlayback,
    claimSession,
    playOnDevice,
    sendPositionUpdate,
    incomingState,
    playingOnDevice,
    transferPlayback,
    sendCommand,
    clearIncomingState,
  }), [
    isConnected, devices, activeSession, isActiveDevice,
    announcePlayback, claimSession, playOnDevice, sendPositionUpdate,
    incomingState, playingOnDevice, transferPlayback, sendCommand, clearIncomingState,
  ]);

  return (
    <ConnectContext.Provider value={value}>
      {children}
    </ConnectContext.Provider>
  );
}
