"use client";

import { useState, useRef, useEffect, useMemo } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { useConnect } from "@/contexts/ConnectContext";

function formatShowDate(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export default function UserMenu() {
  const { user, isLoading, signOut } = useAuth();
  const { devices, isConnected, activeSession } = useConnect();
  const [open, setOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const localDeviceId = useMemo(() => {
    if (typeof window === "undefined") return "";
    return localStorage.getItem("deadly_device_id") ?? "";
  }, []);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    if (open) {
      document.addEventListener("mousedown", handleClickOutside);
      return () =>
        document.removeEventListener("mousedown", handleClickOutside);
    }
  }, [open]);

  if (isLoading) return null;

  if (!user) {
    const path = typeof window !== "undefined" ? window.location.pathname : "/";
    const callbackUrl = path.startsWith("/api/") ? "/" : path;
    return (
      <a
        href={`/signin?callbackUrl=${encodeURIComponent(callbackUrl)}`}
        className="rounded-md border border-white/20 px-3 py-1.5 text-sm text-white/70 transition hover:border-white/40 hover:text-white"
      >
        Sign in
      </a>
    );
  }

  const firstName = user.name?.split(" ")[0] ?? "User";

  return (
    <div className="relative" ref={menuRef}>
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 rounded-md px-2 py-1 text-sm text-white/80 transition hover:bg-white/10"
      >
        {user.image ? (
          <img
            src={user.image}
            alt=""
            className="h-6 w-6 rounded-full"
            referrerPolicy="no-referrer"
          />
        ) : (
          <span className="flex h-6 w-6 items-center justify-center rounded-full bg-deadly-accent text-xs font-bold text-white">
            {firstName[0].toUpperCase()}
          </span>
        )}
        <span>{firstName}</span>
      </button>

      {open && (
        <div className="absolute right-0 top-full z-50 mt-2 w-56 rounded-lg border border-white/10 bg-deadly-surface p-3 shadow-lg">
          <div className="mb-2 border-b border-white/10 pb-2">
            {user.name && (
              <p className="text-sm font-medium text-white">{user.name}</p>
            )}
            {user.email && (
              <p className="text-xs text-white/50">{user.email}</p>
            )}
          </div>
          {isConnected && activeSession?.state && (
            <div className="mb-2 border-b border-white/10 pb-2">
              <p className="mb-1 text-[10px] font-medium uppercase tracking-wider text-white/30">
                Now Playing
              </p>
              <p className="truncate text-xs font-medium text-deadly-highlight">
                {activeSession.state.date && formatShowDate(activeSession.state.date)}
                {activeSession.state.venue && ` — ${activeSession.state.venue}`}
              </p>
              <p className="truncate text-[10px] text-white/40">
                on {activeSession.deviceName}
              </p>
            </div>
          )}
          {isConnected && devices.length > 0 && (
            <div className="mb-2 border-b border-white/10 pb-2">
              <p className="mb-1.5 text-[10px] font-medium uppercase tracking-wider text-white/30">
                Devices
              </p>
              <div className="space-y-0.5">
                {devices.map((device) => {
                  const isCurrent = device.deviceId === localDeviceId;
                  const isPlaying = activeSession?.deviceId === device.deviceId;
                  return (
                    <div
                      key={device.deviceId}
                      className={`rounded-md px-2 py-1 ${isCurrent ? "bg-white/5" : ""}`}
                    >
                      <div className="flex items-center gap-2">
                        <span className={`text-xs ${isCurrent ? "text-deadly-highlight" : "text-white/30"}`}>
                          {device.type === "ios" ? "iPhone" : device.type === "android" ? "Android" : "Browser"}
                        </span>
                        <span className={`flex-1 truncate text-xs ${isCurrent ? "text-white" : "text-white/60"}`}>
                          {device.name}
                          {isCurrent && (
                            <span className="ml-1 text-deadly-highlight">(this device)</span>
                          )}
                        </span>
                        {isPlaying && (
                          <span className="flex h-4 items-end gap-[2px] text-deadly-highlight">
                            <span className="inline-block w-[3px] animate-[eq-bar_0.8s_ease-in-out_infinite_alternate] rounded-sm bg-current" style={{ height: "40%" }} />
                            <span className="inline-block w-[3px] animate-[eq-bar_0.8s_ease-in-out_0.2s_infinite_alternate] rounded-sm bg-current" style={{ height: "70%" }} />
                            <span className="inline-block w-[3px] animate-[eq-bar_0.8s_ease-in-out_0.4s_infinite_alternate] rounded-sm bg-current" style={{ height: "55%" }} />
                          </span>
                        )}
                      </div>
                      {isPlaying && activeSession?.state && (
                        <p className="mt-0.5 truncate pl-[calc(0.5rem+1ch)] text-[10px] text-deadly-highlight/70">
                          {activeSession.state.date && formatShowDate(activeSession.state.date)}
                          {activeSession.state.venue && ` — ${activeSession.state.venue}`}
                        </p>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
          <button
            onClick={() => {
              setOpen(false);
              signOut();
            }}
            className="w-full rounded-md px-2 py-1.5 text-left text-sm text-white/70 transition hover:bg-white/10 hover:text-white"
          >
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}
