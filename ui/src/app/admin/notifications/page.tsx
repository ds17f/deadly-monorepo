"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect, useState, useCallback } from "react";

const TITLE_MAX = 120;
const BODY_MAX = 2000;

interface AdminNotification {
  id: number;
  title: string;
  body: string;
  level: "info" | "warn";
  created_at: number;
  expires_at: number | null;
  deleted_at: number | null;
}

function formatTs(ts: number | null): string {
  if (!ts) return "—";
  return new Date(ts * 1000).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function NotificationsAdminPage() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const [items, setItems] = useState<AdminNotification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [level, setLevel] = useState<"info" | "warn">("info");
  const [submitting, setSubmitting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const res = await fetch("/api/admin/notifications", { credentials: "include" });
      if (res.status === 403) {
        setError("Forbidden");
        return;
      }
      if (!res.ok) throw new Error("Failed to load");
      const data = await res.json();
      setItems(data.notifications);
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
    fetchData();
  }, [authLoading, user?.isAdmin, router, fetchData]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !body.trim()) return;
    setSubmitting(true);
    try {
      const res = await fetch("/api/admin/notifications", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ title: title.trim(), body: body.trim(), level }),
      });
      if (!res.ok) {
        const b = await res.json().catch(() => ({}));
        alert(b.error || `Send failed (${res.status})`);
        return;
      }
      setTitle("");
      setBody("");
      setLevel("info");
      await fetchData();
    } finally {
      setSubmitting(false);
    }
  };

  const retire = async (id: number) => {
    const res = await fetch(`/api/admin/notifications/${id}`, {
      method: "DELETE",
      credentials: "include",
    });
    if (!res.ok && res.status !== 204) {
      const b = await res.json().catch(() => ({}));
      alert(b.error || `Delete failed (${res.status})`);
    }
    setConfirmDelete(null);
    await fetchData();
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

  const now = Math.floor(Date.now() / 1000);
  const statusOf = (n: AdminNotification): { label: string; cls: string } => {
    if (n.deleted_at) return { label: "retired", cls: "bg-zinc-600/20 text-zinc-400" };
    if (n.expires_at && n.expires_at < now) return { label: "expired", cls: "bg-zinc-600/20 text-zinc-400" };
    return { label: "live", cls: "bg-green-600/20 text-green-400" };
  };

  return (
    <div className="min-h-screen bg-deadly-bg text-zinc-100 p-6">
      <div className="max-w-3xl mx-auto space-y-6">
        <nav className="flex gap-4 text-sm">
          <a href="/admin" className="text-zinc-400 hover:text-zinc-200">Admin</a>
          <span className="text-deadly-red font-medium">Notifications</span>
        </nav>

        <div>
          <h1 className="text-2xl font-bold">Notifications</h1>
          <p className="mt-1 text-sm text-zinc-400">
            Publish a message to everybody. It lands in-app on each device the next
            time it foregrounds. Dismiss/read state is per-device.
          </p>
        </div>

        {/* Compose */}
        <form onSubmit={submit} className="bg-deadly-surface rounded-lg p-4 border border-zinc-800 space-y-3">
          <div>
            <div className="flex justify-between mb-1">
              <label className="text-xs text-zinc-400">Title</label>
              <span className="text-xs text-zinc-600">{title.length}/{TITLE_MAX}</span>
            </div>
            <input
              type="text"
              required
              maxLength={TITLE_MAX}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="What's up?"
              className="w-full bg-zinc-800 border border-zinc-700 rounded px-3 py-1.5 text-sm"
            />
          </div>
          <div>
            <div className="flex justify-between mb-1">
              <label className="text-xs text-zinc-400">Message</label>
              <span className="text-xs text-zinc-600">{body.length}/{BODY_MAX}</span>
            </div>
            <textarea
              required
              maxLength={BODY_MAX}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              rows={4}
              placeholder="The full announcement…"
              className="w-full bg-zinc-800 border border-zinc-700 rounded px-3 py-2 text-sm resize-y"
            />
          </div>
          <div className="flex items-center justify-between gap-3">
            <label className="flex items-center gap-2 text-sm text-zinc-400">
              Level
              <select
                value={level}
                onChange={(e) => setLevel(e.target.value as "info" | "warn")}
                className="bg-zinc-800 border border-zinc-700 rounded px-2 py-1 text-sm"
              >
                <option value="info">Info</option>
                <option value="warn">Warning</option>
              </select>
            </label>
            <button
              type="submit"
              disabled={submitting || !title.trim() || !body.trim()}
              className="px-4 py-1.5 bg-deadly-red text-white rounded text-sm hover:bg-red-700 disabled:opacity-50"
            >
              {submitting ? "Sending…" : "Send to everybody"}
            </button>
          </div>
        </form>

        {/* Sent list */}
        <div className="space-y-2">
          <h2 className="text-sm font-medium text-zinc-400">Sent</h2>
          {items.length === 0 ? (
            <div className="bg-deadly-surface rounded-lg border border-zinc-800 px-4 py-8 text-center text-zinc-500">
              Nothing sent yet.
            </div>
          ) : (
            items.map((n) => {
              const status = statusOf(n);
              return (
                <div key={n.id} className="bg-deadly-surface rounded-lg border border-zinc-800 p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className={`px-2 py-0.5 rounded text-xs font-medium ${status.cls}`}>{status.label}</span>
                        {n.level === "warn" && (
                          <span className="px-2 py-0.5 rounded text-xs font-medium bg-yellow-600/20 text-yellow-400">warn</span>
                        )}
                        <span className="text-xs text-zinc-500">{formatTs(n.created_at)}</span>
                      </div>
                      <p className="mt-1.5 font-medium text-zinc-100 truncate">{n.title}</p>
                      <p className="mt-0.5 text-sm text-zinc-400 whitespace-pre-wrap break-words">{n.body}</p>
                    </div>
                    {!n.deleted_at && (
                      confirmDelete === n.id ? (
                        <div className="flex items-center gap-2 shrink-0">
                          <button onClick={() => retire(n.id)} className="px-2 py-0.5 bg-red-600 text-white rounded text-xs hover:bg-red-700">Confirm</button>
                          <button onClick={() => setConfirmDelete(null)} className="px-2 py-0.5 text-zinc-400 text-xs hover:text-zinc-200">Cancel</button>
                        </div>
                      ) : (
                        <button
                          onClick={() => setConfirmDelete(n.id)}
                          className="shrink-0 px-2 py-0.5 rounded text-xs bg-red-600/20 text-red-400 hover:bg-red-600/40"
                        >
                          Retire
                        </button>
                      )
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
