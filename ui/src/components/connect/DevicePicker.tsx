"use client";

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
  const { devices, playOnDevice } = useConnect();

  const otherDevices = devices.filter((d) => {
    // Don't show current device
    const localId = typeof window !== "undefined" ? localStorage.getItem("deadly_device_id") : null;
    return d.deviceId !== localId;
  });

  if (otherDevices.length === 0) {
    return (
      <div className="absolute right-0 top-full z-50 mt-2 w-64 rounded-lg border border-white/10 bg-deadly-bg p-4 shadow-xl">
        <p className="text-sm text-white/40">No other devices connected</p>
        <p className="mt-1 text-xs text-white/20">
          Open The Deadly on another device to transfer playback
        </p>
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
      <p className="mb-2 text-xs font-medium text-white/50">Play on device</p>
      <div className="space-y-1">
        {otherDevices.map((device) => (
          <button
            key={device.deviceId}
            onClick={() => {
              if (currentState) {
                playOnDevice(device.deviceId, currentState);
              }
              onClose();
            }}
            disabled={!currentState}
            className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-white/5 disabled:opacity-30"
          >
            <span className="text-xs text-white/30">
              {DEVICE_ICONS[device.type] ?? device.type}
            </span>
            <span className="flex-1 truncate text-white/80">{device.name}</span>
            {device.capabilities.includes("playback") && (
              <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" className="text-white/20">
                <path d="M8 5v14l11-7z" />
              </svg>
            )}
          </button>
        ))}
      </div>
    </div>
  );
}
