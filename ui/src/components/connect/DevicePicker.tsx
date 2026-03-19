"use client";

import { useMemo } from "react";
import { useConnect } from "@/contexts/ConnectContext";
import type { PlaybackState } from "@/contexts/ConnectContext";

const DEVICE_ICONS: Record<string, string> = {
  ios: "iPhone",
  android: "Android",
  web: "Browser",
};

export default function DevicePicker({
  currentState,
  onClose,
}: {
  currentState: PlaybackState | null;
  onClose: () => void;
}) {
  const { devices, userState, playOnDevice, claimSession } = useConnect();

  const localDeviceId = useMemo(() => {
    if (typeof window === "undefined") return "";
    return localStorage.getItem("deadly_device_id") ?? "";
  }, []);

  if (devices.length === 0) {
    return (
      <div className="absolute right-0 top-full z-50 mt-2 w-64 rounded-lg border border-white/10 bg-deadly-bg p-4 shadow-xl">
        <p className="text-sm text-white/40">No devices connected</p>
        <button
          onClick={onClose}
          className="mt-3 text-xs text-white/30 hover:text-white/50"
        >
          Close
        </button>
      </div>
    );
  }

  return (
    <div className="absolute right-0 top-full z-50 mt-2 w-64 rounded-lg border border-white/10 bg-deadly-bg p-3 shadow-xl">
      <p className="mb-2 text-xs font-medium text-white/50">Devices</p>
      <div className="space-y-1">
        {devices.map((device) => {
          const isLocal = device.deviceId === localDeviceId;
          const isPlayingDevice = userState?.activeDeviceId === device.deviceId;

          return (
            <button
              key={device.deviceId}
              onClick={() => {
                if (!isPlayingDevice && currentState) {
                  if (isLocal) {
                    claimSession();
                    window.dispatchEvent(new CustomEvent("connect:play_on", { detail: currentState }));
                  } else {
                    playOnDevice(device.deviceId, currentState);
                  }
                }
                onClose();
              }}
              disabled={isPlayingDevice || !currentState}
              className={`flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm transition-colors ${
                isPlayingDevice
                  ? "bg-white/5 cursor-default"
                  : "hover:bg-white/5 disabled:opacity-30"
              }`}
            >
              <span className={`text-xs ${isPlayingDevice ? "text-deadly-highlight" : "text-white/30"}`}>
                {DEVICE_ICONS[device.type] ?? device.type}
              </span>
              <span className={`flex-1 truncate ${isPlayingDevice ? "text-white" : "text-white/80"}`}>
                {device.name}
                {isLocal && (
                  <span className="ml-1 text-xs text-white/30">(this device)</span>
                )}
              </span>
              {isPlayingDevice && (
                <span className="flex h-4 items-end gap-[2px] text-deadly-highlight">
                  {userState?.isPlaying ? (
                    <>
                      <span className="inline-block w-[3px] animate-[eq-bar_0.8s_ease-in-out_infinite_alternate] rounded-sm bg-current" style={{ height: "40%" }} />
                      <span className="inline-block w-[3px] animate-[eq-bar_0.8s_ease-in-out_0.2s_infinite_alternate] rounded-sm bg-current" style={{ height: "70%" }} />
                      <span className="inline-block w-[3px] animate-[eq-bar_0.8s_ease-in-out_0.4s_infinite_alternate] rounded-sm bg-current" style={{ height: "55%" }} />
                    </>
                  ) : (
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M6 19h4V5H6zm8-14v14h4V5z" />
                    </svg>
                  )}
                </span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
