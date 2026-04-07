"use client";

import { useConnect } from "@/contexts/ConnectContext";
import type { DeviceType } from "@/types/connect";

function deviceTypeLabel(type: DeviceType): string {
  switch (type) {
    case "ios": return "iOS";
    case "android": return "Android";
    case "web": return "Web";
  }
}

function deviceTypeIcon(type: DeviceType): string {
  switch (type) {
    case "ios": return "󰀀"; // fallback to text if icon font not available
    case "android": return "Android";
    case "web": return "Web";
  }
}

export default function DeviceList() {
  const { devices, myDeviceId } = useConnect();

  if (devices.length === 0) return null;

  return (
    <div className="mt-2 border-t border-white/10 pt-2">
      <p className="mb-1.5 text-xs font-medium text-white/40 uppercase tracking-wide">
        Connected Devices
      </p>
      <ul className="space-y-1">
        {devices.map((device) => {
          const isMe = device.deviceId === myDeviceId;
          return (
            <li
              key={device.deviceId}
              className="flex items-center gap-2 rounded-md px-2 py-1"
            >
              <span className="text-xs text-white/30 w-10 shrink-0">
                {deviceTypeLabel(device.deviceType)}
              </span>
              <span
                className={`truncate text-sm ${
                  isMe ? "text-white font-medium" : "text-white/60"
                }`}
              >
                {device.deviceName}
                {isMe && (
                  <span className="ml-1.5 text-xs text-white/30">(this device)</span>
                )}
              </span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
