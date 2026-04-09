"use client";

import { useConnect } from "@/contexts/ConnectContext";
import { usePlayer } from "@/contexts/PlayerContext";
import type { DeviceType } from "@/types/connect";

function deviceTypeLabel(type: DeviceType): string {
  switch (type) {
    case "ios": return "iOS";
    case "android": return "Android";
    case "web": return "Web";
  }
}

function Spinner() {
  return (
    <svg className="animate-spin h-3.5 w-3.5 text-white/40" viewBox="0 0 24 24" fill="currentColor">
      <path d="M12 2a10 10 0 0 1 10 10h-2a8 8 0 0 0-8-8V2z" />
    </svg>
  );
}

export default function DeviceList() {
  const { devices, state: connectState, myDeviceId } = useConnect();
  const { pendingTransfer, transferTo, isActiveDevice, isRemoteControlling } = usePlayer();

  if (devices.length === 0) return null;

  const hasSession = connectState?.showId != null;

  return (
    <div className="mt-2 border-t border-white/10 pt-2">
      <p className="mb-1.5 text-xs font-medium text-white/40 uppercase tracking-wide">
        Connected Devices
      </p>
      <ul className="space-y-1">
        {devices.map((device) => {
          const isMe = device.deviceId === myDeviceId;
          const isDeviceActive = device.deviceId === connectState?.activeDeviceId;
          const isPending = pendingTransfer === device.deviceId;

          let action: React.ReactNode = null;
          if (hasSession) {
            if (isPending) {
              action = <Spinner />;
            } else if (isDeviceActive) {
              action = <span className="text-xs text-deadly-highlight">Playing</span>;
            } else if (isMe && isRemoteControlling) {
              action = (
                <button
                  onClick={() => transferTo(myDeviceId!)}
                  disabled={pendingTransfer !== null}
                  className="text-xs text-white/50 hover:text-white transition-colors disabled:opacity-30"
                >
                  Play here
                </button>
              );
            } else if (!isMe) {
              action = (
                <button
                  onClick={() => transferTo(device.deviceId)}
                  disabled={pendingTransfer !== null}
                  className="text-xs text-white/50 hover:text-white transition-colors disabled:opacity-30"
                >
                  Play
                </button>
              );
            }
          }

          return (
            <li
              key={device.deviceId}
              className="flex items-center gap-2 rounded-md px-2 py-1"
            >
              <span className="text-xs text-white/30 w-10 shrink-0">
                {deviceTypeLabel(device.deviceType)}
              </span>
              <span
                className={`truncate text-sm flex-1 ${
                  isMe ? "text-white font-medium" : "text-white/60"
                }`}
              >
                {device.deviceName}
                {isMe && (
                  <span className="ml-1.5 text-xs text-white/30">(this device)</span>
                )}
              </span>
              {action}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
