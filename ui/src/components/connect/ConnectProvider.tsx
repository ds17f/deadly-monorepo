"use client";

import { useState, useEffect, useRef, useCallback, useMemo } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { ConnectContext } from "@/contexts/ConnectContext";
import type { ConnectDevice, PlaybackState, ActiveSession, UserPlaybackState, ConnectConfig } from "@/contexts/ConnectContext";
import { DEFAULT_CONNECT_CONFIG } from "@/contexts/ConnectContext";
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
  const [userState, setUserState] = useState<UserPlaybackState | null>(null);
  const [connectConfig, setConnectConfig] = useState<ConnectConfig>(DEFAULT_CONNECT_CONFIG);
  const wsRef = useRef<ConnectWebSocket | null>(null);

  const localDeviceId = useMemo(() => {
    if (typeof window === "undefined") return "";
    return generateDeviceId();
  }, []);

  const isActiveDevice = userState?.activeDeviceId === localDeviceId && localDeviceId !== "";

  useEffect(() => {
    if (!user?.id) {
      wsRef.current?.close();
      wsRef.current = null;
      setIsConnected(false);
      setDevices([]);
      setActiveSession(null);
      setUserState(null);
      return;
    }

    const deviceId = generateDeviceId();
    const ws = new ConnectWebSocket({
      url: getWsUrl(),
      onMessage: (data: unknown) => {
        const msg = data as {
          type: string;
          devices?: ConnectDevice[];
          state?: PlaybackState | UserPlaybackState;
          session?: ActiveSession | null;
          fromDeviceName?: string;
        };
        switch (msg.type) {
          case "config": {
            const cfg = (msg as Record<string, unknown>).config as ConnectConfig | undefined;
            if (cfg) setConnectConfig(cfg);
            break;
          }
          case "devices":
            setDevices(msg.devices ?? []);
            break;
          case "active_session":
            setActiveSession(msg.session ?? null);
            break;
          case "user_state": {
            const incoming = (msg.state as UserPlaybackState) ?? null;
            setUserState(prev => {
              if (!incoming) return null;
              return {
                ...incoming,
                // Client-side timestamp so interpolation baseline is accurate
                updatedAt: Date.now(),
                // Don't let durationMs regress to 0 when we already know the duration
                durationMs: incoming.durationMs || prev?.durationMs || 0,
                // Don't let trackTitle regress to undefined when we already have it
                trackTitle: incoming.trackTitle ?? prev?.trackTitle,
              };
            });
            break;
          }
          case "session_play_on":
            if (msg.state) {
              const playOnState = msg.state as PlaybackState;
              // Compensate position for server relay + network transit time
              const relayedAt = (msg as Record<string, unknown>).relayedAt as number | undefined;
              const adjusted = relayedAt && playOnState.status === "playing"
                ? { ...playOnState, positionMs: playOnState.positionMs + (Date.now() - relayedAt) }
                : playOnState;
              window.dispatchEvent(new CustomEvent("connect:play_on", {
                detail: { ...adjusted, fromDeviceName: msg.fromDeviceName },
              }));
            }
            break;
          case "session_stop":
            window.dispatchEvent(new CustomEvent("connect:session_stop"));
            break;
          case "position_update": {
            const state = msg.state as PlaybackState;
            setUserState(prev => prev ? {
              ...prev,
              positionMs: state.positionMs,
              trackIndex: state.trackIndex,
              durationMs: state.durationMs ?? prev.durationMs,
              trackTitle: state.trackTitle ?? prev.trackTitle,
              updatedAt: Date.now(),
            } : prev);
            break;
          }
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

  const claimSession = useCallback((state?: PlaybackState) => {
    wsRef.current?.send({ type: "session_claim", ...(state ? { state } : {}) });
  }, []);

  const playOnDevice = useCallback((deviceId: string, state: PlaybackState) => {
    wsRef.current?.send({ type: "session_play_on", targetDeviceId: deviceId, state });
  }, []);

  const sendPositionUpdate = useCallback((state: PlaybackState) => {
    wsRef.current?.send({ type: "position_update", state });
  }, []);

  const sendCommand = useCallback((action: string, seekMs?: number) => {
    wsRef.current?.send({
      type: "command",
      command: { action, ...(seekMs !== undefined ? { seekMs } : {}) },
    });
  }, []);

  const clearState = useCallback(() => {
    wsRef.current?.send({ type: "state_clear" });
  }, []);

  const value = useMemo(() => ({
    isConnected,
    devices,
    activeSession,
    userState,
    isActiveDevice,
    connectConfig,
    setUserState,
    announcePlayback,
    claimSession,
    playOnDevice,
    sendPositionUpdate,
    sendCommand,
    clearState,
  }), [
    isConnected, devices, activeSession, userState, isActiveDevice, connectConfig, setUserState,
    announcePlayback, claimSession, playOnDevice, sendPositionUpdate, sendCommand, clearState,
  ]);

  return (
    <ConnectContext.Provider value={value}>
      {children}
    </ConnectContext.Provider>
  );
}
