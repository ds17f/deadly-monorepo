"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect, useState, useCallback } from "react";

export default function GlobalSettingsPage() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const [connectEnabled, setConnectEnabled] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const fetchSettings = useCallback(async () => {
    try {
      const res = await fetch("/api/admin/connect/settings", { credentials: "include" });
      if (res.status === 403) {
        setError("Forbidden");
        return;
      }
      if (!res.ok) throw new Error("Failed to load");
      const data = await res.json();
      setConnectEnabled(data.connectEnabled);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (authLoading) return;
    if (!user?.isAdmin) {
      router.replace("/");
      return;
    }
    fetchSettings();
  }, [authLoading, user?.isAdmin, router, fetchSettings]);

  const toggleConnect = async () => {
    if (connectEnabled == null || saving) return;
    setSaving(true);
    const next = !connectEnabled;
    try {
      const res = await fetch("/api/admin/connect/settings", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ connectEnabled: next }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        alert(body.error || `Failed to save (${res.status})`);
        return;
      }
      const data = await res.json();
      setConnectEnabled(data.connectEnabled);
    } finally {
      setSaving(false);
    }
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
          <div className="p-4 flex items-center justify-between gap-4">
            <div className="min-w-0">
              <div className="text-sm font-medium text-zinc-100">Connect</div>
              <p className="text-sm text-zinc-400 mt-1">
                Cross-device playback sync. When off, the server refuses all
                Connect/WebSocket sessions — clients hide Connect entirely. Takes
                effect immediately for new connections; no restart needed.
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
        </div>

        <p className="text-xs text-zinc-500">
          Connect is{" "}
          <span className={connectEnabled ? "text-green-400" : "text-zinc-400"}>
            {connectEnabled ? "enabled" : "disabled"}
          </span>{" "}
          globally.
        </p>
      </div>
    </div>
  );
}
