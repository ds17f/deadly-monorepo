"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useConnect } from "@/contexts/ConnectContext";

function formatMs(ms: number): string {
  const totalSec = Math.floor(ms / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${min}:${sec.toString().padStart(2, "0")}`;
}

function timeAgo(ts: number): string {
  const sec = Math.floor((Date.now() - ts) / 1000);
  if (sec < 5) return "just now";
  if (sec < 60) return `${sec}s ago`;
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.floor(min / 60);
  return `${hr}h ago`;
}

function DeviceIcon({ type }: { type: string }) {
  if (type === "ios") return <span title="iOS">📱</span>;
  if (type === "android") return <span title="Android">📱</span>;
  return <span title="Web">💻</span>;
}

export default function ProfilePage() {
  const { user, isLoading } = useAuth();
  const { isConnected, devices, userState } = useConnect();

  if (isLoading) return null;

  if (!user) {
    return (
      <div className="py-12 text-center text-white/50">
        <p>Sign in to view your profile.</p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* User info */}
      <section>
        <div className="flex items-center gap-4">
          {user.image ? (
            <img
              src={user.image}
              alt=""
              className="h-14 w-14 rounded-full"
              referrerPolicy="no-referrer"
            />
          ) : (
            <span className="flex h-14 w-14 items-center justify-center rounded-full bg-deadly-accent text-xl font-bold text-white">
              {(user.name?.[0] ?? "U").toUpperCase()}
            </span>
          )}
          <div>
            {user.name && <h1 className="text-xl font-bold">{user.name}</h1>}
            {user.email && (
              <p className="text-sm text-white/50">{user.email}</p>
            )}
          </div>
        </div>
      </section>

      {/* Connect state */}
      <section className="space-y-4">
        <h2 className="text-lg font-semibold">Connect</h2>

        <div className="rounded-lg border border-white/10 bg-white/5 p-4">
          <div className="mb-3 flex items-center gap-2 text-sm">
            <span
              className={`h-2 w-2 rounded-full ${isConnected ? "bg-green-400" : "bg-red-400"}`}
            />
            <span className="text-white/60">
              {isConnected ? "Connected" : "Disconnected"}
            </span>
          </div>

          {/* Devices */}
          {devices.length > 0 && (
            <div className="mb-4">
              <h3 className="mb-2 text-sm font-medium text-white/40">
                Devices ({devices.length})
              </h3>
              <div className="space-y-1">
                {devices.map((d) => (
                  <div
                    key={d.deviceId}
                    className="flex items-center gap-2 text-sm"
                  >
                    <DeviceIcon type={d.type} />
                    <span
                      className={
                        userState?.activeDeviceId === d.deviceId
                          ? "font-medium text-deadly-accent"
                          : "text-white/70"
                      }
                    >
                      {d.name}
                    </span>
                    {userState?.activeDeviceId === d.deviceId && (
                      <span className="rounded bg-deadly-accent/20 px-1.5 py-0.5 text-xs text-deadly-accent">
                        active
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Server playback state */}
          <div>
            <h3 className="mb-2 text-sm font-medium text-white/40">
              Server State
            </h3>
            {userState ? (
              <div className="space-y-2 text-sm">
                <div className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
                  <span className="text-white/40">Status</span>
                  <span>
                    {userState.isPlaying ? (
                      <span className="text-green-400">Playing</span>
                    ) : userState.activeDeviceId ? (
                      <span className="text-yellow-400">Paused</span>
                    ) : (
                      <span className="text-white/50">Parked</span>
                    )}
                  </span>

                  <span className="text-white/40">Show</span>
                  <span>{userState.date ?? userState.showId}</span>

                  {userState.venue && (
                    <>
                      <span className="text-white/40">Venue</span>
                      <span>{userState.venue}</span>
                    </>
                  )}

                  {userState.location && (
                    <>
                      <span className="text-white/40">Location</span>
                      <span>{userState.location}</span>
                    </>
                  )}

                  <span className="text-white/40">Track</span>
                  <span>
                    #{userState.trackIndex + 1}
                    {userState.trackTitle && ` — ${userState.trackTitle}`}
                  </span>

                  <span className="text-white/40">Position</span>
                  <span>
                    {formatMs(userState.positionMs)}
                    {userState.durationMs > 0 &&
                      ` / ${formatMs(userState.durationMs)}`}
                  </span>

                  <span className="text-white/40">Recording</span>
                  <span className="font-mono text-xs text-white/60">
                    {userState.recordingId}
                  </span>

                  {userState.activeDeviceName && (
                    <>
                      <span className="text-white/40">Active Device</span>
                      <span>
                        {userState.activeDeviceName} ({userState.activeDeviceType})
                      </span>
                    </>
                  )}

                  <span className="text-white/40">Updated</span>
                  <span className="text-white/60">
                    {timeAgo(userState.updatedAt)}
                  </span>
                </div>
              </div>
            ) : (
              <p className="text-sm text-white/40">
                No playback state on server.
              </p>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}
