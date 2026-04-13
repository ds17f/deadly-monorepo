"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { useConnect } from "@/contexts/ConnectContext";
import { usePlayer } from "@/contexts/PlayerContext";
import type { DeviceType } from "@/types/connect";

function DeviceIcon({ type, active }: { type: DeviceType; active: boolean }) {
  const color = active ? "text-deadly-highlight" : "text-white/50";
  switch (type) {
    case "ios":
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" className={color}>
          <path d="M15.5 1h-8C6.12 1 5 2.12 5 3.5v17C5 21.88 6.12 23 7.5 23h8c1.38 0 2.5-1.12 2.5-2.5v-17C18 2.12 16.88 1 15.5 1zm-4 21c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm4.5-4H7V4h9v14z" />
        </svg>
      );
    case "android":
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" className={color}>
          <path d="M15.5 1h-8C6.12 1 5 2.12 5 3.5v17C5 21.88 6.12 23 7.5 23h8c1.38 0 2.5-1.12 2.5-2.5v-17C18 2.12 16.88 1 15.5 1zm-4 21c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm4.5-4H7V4h9v14z" />
        </svg>
      );
    case "web":
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" className={color}>
          <path d="M20 18c1.1 0 1.99-.9 1.99-2L22 6c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2H0v2h24v-2h-4zM4 6h16v10H4V6z" />
        </svg>
      );
  }
}

function Spinner() {
  return (
    <svg className="animate-spin h-4 w-4 text-white/30" viewBox="0 0 24 24" fill="currentColor">
      <path d="M12 2a10 10 0 0 1 10 10h-2a8 8 0 0 0-8-8V2z" />
    </svg>
  );
}

function SpeakerIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" className="text-deadly-highlight">
      <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z" />
    </svg>
  );
}

function SpeakerMutedIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" className="text-white/40">
      <path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z" />
    </svg>
  );
}

function VolumeSlider({
  volume,
  onCommit,
}: {
  volume: number;
  onCommit: (v: number) => void;
}) {
  const [localVolume, setLocalVolume] = useState(volume);
  const isDraggingRef = useRef(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Sync from context when not dragging
  useEffect(() => {
    if (!isDraggingRef.current) {
      setLocalVolume(volume);
    }
  }, [volume]);

  const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    isDraggingRef.current = true;
    const v = Number(e.target.value);
    setLocalVolume(v);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => onCommit(v), 150);
  }, [onCommit]);

  const handleCommit = useCallback(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    isDraggingRef.current = false;
    onCommit(localVolume);
  }, [onCommit, localVolume]);

  return (
    <div className="flex items-center gap-2 px-2.5 py-2">
      <SpeakerMutedIcon />
      <input
        type="range"
        min={0}
        max={100}
        value={localVolume}
        onChange={handleChange}
        onMouseUp={handleCommit}
        onTouchEnd={handleCommit}
        className="flex-1 h-1 appearance-none rounded-full bg-white/20 accent-deadly-highlight cursor-pointer"
      />
      <SpeakerIcon />
    </div>
  );
}

interface DeviceRowProps {
  deviceId: string;
  deviceName: string;
  deviceType: DeviceType;
  isMe: boolean;
  isDeviceActive: boolean;
  isPending: boolean;
  isTappable: boolean;
  onTap: () => void;
}

function DeviceRow({ deviceName, deviceType, isMe, isDeviceActive, isPending, isTappable, onTap }: DeviceRowProps) {
  return (
    <button
      onClick={onTap}
      disabled={!isTappable}
      className={`flex w-full items-center gap-3 rounded-lg px-2.5 py-2 text-left transition-colors ${
        isTappable ? "hover:bg-white/5 cursor-pointer" : "cursor-default"
      }`}
    >
      <DeviceIcon type={deviceType} active={isDeviceActive} />
      <div className="min-w-0 flex-1">
        <p className={`truncate text-sm ${isDeviceActive ? "text-white font-medium" : "text-white/80"}`}>
          {deviceName}
        </p>
        <p className="text-xs text-white/30">
          {isMe ? "This Device" : deviceTypeLabel(deviceType)}
        </p>
      </div>
      <div className="flex-shrink-0">
        {isPending ? (
          <Spinner />
        ) : isDeviceActive ? (
          <div className="flex items-center gap-1">
            <SpeakerIcon />
            <span className="text-xs text-deadly-highlight">Playing</span>
          </div>
        ) : null}
      </div>
    </button>
  );
}

function deviceTypeLabel(type: DeviceType): string {
  switch (type) {
    case "ios": return "iOS";
    case "android": return "Android";
    case "web": return "Web";
  }
}

export default function DeviceList() {
  const { devices, state: connectState, myDeviceId, connected, sendCommand, activeDeviceVolume } = useConnect();
  const { pendingTransfer, transferTo, isActiveDevice, isRemoteControlling } = usePlayer();

  if (devices.length === 0) return null;

  const hasSession = connectState?.showId != null;
  const myDevice = devices.find((d) => d.deviceId === myDeviceId);
  const otherDevices = devices.filter((d) => d.deviceId !== myDeviceId);

  function isTappable(deviceId: string, isDeviceActive: boolean) {
    return hasSession && pendingTransfer === null && !isDeviceActive;
  }

  return (
    <div>
      {/* This Device */}
      {myDevice && (
        <>
          <p className="mb-1 px-2.5 text-[11px] font-medium text-white/30 uppercase tracking-wider">
            This Device
          </p>
          <DeviceRow
            deviceId={myDevice.deviceId}
            deviceName={myDevice.deviceName}
            deviceType={myDevice.deviceType}
            isMe
            isDeviceActive={myDevice.deviceId === connectState?.activeDeviceId}
            isPending={pendingTransfer === myDevice.deviceId}
            isTappable={isTappable(myDevice.deviceId, myDevice.deviceId === connectState?.activeDeviceId)}
            onTap={() => transferTo(myDevice.deviceId)}
          />
        </>
      )}

      {/* Other Devices */}
      {otherDevices.length > 0 && (
        <>
          <div className="my-1.5 border-t border-white/10" />
          <p className="mb-1 px-2.5 text-[11px] font-medium text-white/30 uppercase tracking-wider">
            Other Devices
          </p>
          {otherDevices.map((device) => {
            const isDeviceActive = device.deviceId === connectState?.activeDeviceId;
            return (
              <DeviceRow
                key={device.deviceId}
                deviceId={device.deviceId}
                deviceName={device.deviceName}
                deviceType={device.deviceType}
                isMe={false}
                isDeviceActive={isDeviceActive}
                isPending={pendingTransfer === device.deviceId}
                isTappable={isTappable(device.deviceId, isDeviceActive)}
                onTap={() => transferTo(device.deviceId)}
              />
            );
          })}
        </>
      )}

      {/* Volume slider */}
      {hasSession && connectState?.activeDeviceId && (
        <div className="mt-1.5 pt-1.5 border-t border-white/10">
          <VolumeSlider
            volume={activeDeviceVolume ?? 100}
            onCommit={(v) => sendCommand("volume", { volume: v })}
          />
        </div>
      )}

      {/* Stop session */}
      {hasSession && (
        <div className="mt-1.5 pt-1.5 border-t border-white/10">
          <button
            onClick={() => sendCommand("stop")}
            disabled={!connected}
            className="w-full rounded-lg px-2.5 py-2 text-left text-xs text-white/30 hover:bg-white/5 hover:text-white/60 transition-colors disabled:opacity-30"
          >
            Stop session
          </button>
        </div>
      )}
    </div>
  );
}
