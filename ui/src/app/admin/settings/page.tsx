"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect, useState, useCallback } from "react";

interface ConnectSettings {
  connectEnabled: boolean;
  connectMinProtocol: number;
}

export default function GlobalSettingsPage() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const [connectEnabled, setConnectEnabled] = useState<boolean | null>(null);
  const [minProtocol, setMinProtocol] = useState<number | null>(null);
  const [minDraft, setMinDraft] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const applySettings = useCallback((data: ConnectSettings) => {
    setConnectEnabled(data.connectEnabled);
    setMinProtocol(data.connectMinProtocol);
    setMinDraft(String(data.connectMinProtocol));
  }, []);

  const fetchSettings = useCallback(async () => {
    try {
      const res = await fetch("/api/admin/connect/settings", { credentials: "include" });
      if (res.status === 403) {
        setError("Forbidden");
        return;
      }
      if (!res.ok) throw new Error("Failed to load");
      applySettings(await res.json());
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load");
    } finally {
      setLoading(false);
    }
  }, [applySettings]);

  useEffect(() => {
    if (authLoading) return;
    if (!user?.isAdmin) {
      router.replace("/");
      return;
    }
    fetchSettings();
  }, [authLoading, user?.isAdmin, router, fetchSettings]);

  // Persist a partial change; the server applies it and returns the full state
  // (and disconnects any live device the new gate no longer admits).
  const save = useCallback(async (patch: Partial<ConnectSettings>) => {
    if (saving) return;
    setSaving(true);
    try {
      const res = await fetch("/api/admin/connect/settings", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(patch),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        alert(body.error || `Failed to save (${res.status})`);
        return;
      }
      applySettings(await res.json());
    } finally {
      setSaving(false);
    }
  }, [saving, applySettings]);

  const toggleConnect = () => {
    if (connectEnabled == null) return;
    save({ connectEnabled: !connectEnabled });
  };

  const saveMinProtocol = () => {
    const n = parseInt(minDraft, 10);
    if (!Number.isFinite(n) || n < 0) {
      setMinDraft(String(minProtocol ?? 0));
      return;
    }
    if (n === minProtocol) return;
    save({ connectMinProtocol: n });
  };

  if (authLoading || loading) {
    return (
      <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
        <p className="text-zinc-400">Loading...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
        <p className="text-red-400">{error}</p>
      </div>
    );
  }

  const minDirty = minDraft !== String(minProtocol ?? "");

  return (
    <div className="min-h-screen bg-deadly-bg text-zinc-100 p-6">
      <div className="max-w-2xl mx-auto space-y-6">
        {/* Nav */}
        <nav className="flex gap-4 text-sm">
          <a href="/admin" className="text-zinc-400 hover:text-zinc-200">
            Admin
          </a>
          <span className="text-deadly-red font-medium">Global Settings</span>
        </nav>

        <h1 className="text-2xl font-bold">Global Settings</h1>

        <div className="bg-deadly-surface rounded-lg border border-zinc-800 divide-y divide-zinc-800">
          {/* Connect on/off */}
          <div className="p-4 flex items-center justify-between gap-4">
            <div className="min-w-0">
              <div className="text-sm font-medium text-zinc-100">Connect</div>
              <p className="text-sm text-zinc-400 mt-1">
                Cross-device playback sync. When off, the server refuses all
                Connect/WebSocket sessions — clients hide Connect entirely. Takes
                effect immediately; live sessions are disconnected.
              </p>
            </div>
            <button
              onClick={toggleConnect}
              disabled={saving}
              aria-label="Toggle Connect"
              className={`w-10 h-5 rounded-full relative inline-flex items-center transition-colors shrink-0 disabled:opacity-50 ${
                connectEnabled ? "bg-green-600" : "bg-zinc-600"
              }`}
            >
              <span
                className={`w-4 h-4 rounded-full bg-white transition-transform ${
                  connectEnabled ? "translate-x-[22px]" : "translate-x-[2px]"
                }`}
              />
            </button>
          </div>

          {/* Minimum protocol */}
          <div className="p-4 flex items-start justify-between gap-4">
            <div className="min-w-0">
              <div className="text-sm font-medium text-zinc-100">Minimum protocol version</div>
              <p className="text-sm text-zinc-400 mt-1">
                Lowest client wire-protocol version allowed to connect. <span className="text-zinc-300">0</span> allows
                all. Raise it to gate out older app builds (e.g. <span className="text-zinc-300">2</span> excludes the
                pre-beta fleet) without turning Connect off globally. Can be set
                while Connect is off. Applied immediately — connected clients
                below the floor are disconnected.
              </p>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              <input
                type="number"
                min={0}
                value={minDraft}
                onChange={(e) => setMinDraft(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") saveMinProtocol(); }}
                className="w-16 bg-zinc-800 border border-zinc-700 rounded px-2 py-1 text-sm text-center"
              />
              <button
                onClick={saveMinProtocol}
                disabled={saving || !minDirty}
                className="px-3 py-1 text-sm rounded border border-zinc-700 text-zinc-300 hover:border-zinc-500 disabled:opacity-40 disabled:hover:border-zinc-700"
              >
                Save
              </button>
            </div>
          </div>
        </div>

        <p className="text-xs text-zinc-500">
          Connect is{" "}
          <span className={connectEnabled ? "text-green-400" : "text-zinc-400"}>
            {connectEnabled ? "enabled" : "disabled"}
          </span>{" "}
          globally
          {connectEnabled && (minProtocol ?? 0) > 0 && (
            <> for protocol ≥ <span className="text-zinc-300">{minProtocol}</span></>
          )}
          .
        </p>
      </div>
    </div>
  );
}
