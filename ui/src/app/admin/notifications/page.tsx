"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect, useState, useCallback } from "react";

const TITLE_MAX = 120;
const BODY_MAX = 2000;

type Category = "general" | "release" | "feature" | "outage";
const CATEGORIES: Category[] = ["general", "release", "feature", "outage"];
type Platform = "ios" | "android" | "web";
const PLATFORMS: Platform[] = ["ios", "android", "web"];

interface NotificationEngagement {
  id: number;
  delivered: number;
  displayed: number;
  opened: number;
  archived: number;
  link_clicks: number;
}

interface AdminNotification {
  id: number;
  title: string;
  body: string;
  level: "info" | "warn";
  category: Category;
  min_version: string | null;
  max_version: string | null;
  platforms: string | null;
  created_at: number;
  expires_at: number | null;
  deleted_at: number | null;
}

/** Compact "ios·android ≥2.4.0 <2.3.9" targeting summary, or "" if untargeted. */
function targetingLabel(n: AdminNotification): string {
  const parts: string[] = [];
  if (n.platforms) {
    try {
      const arr = JSON.parse(n.platforms);
      if (Array.isArray(arr) && arr.length > 0) parts.push(arr.join("·"));
    } catch {
      /* ignore malformed */
    }
  }
  if (n.min_version) parts.push(`≥${n.min_version}`);
  if (n.max_version) parts.push(`≤${n.max_version}`);
  return parts.join(" ");
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
  const [stats, setStats] = useState<Record<number, NotificationEngagement>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [level, setLevel] = useState<"info" | "warn">("info");
  const [category, setCategory] = useState<Category>("general");
  const [platforms, setPlatforms] = useState<Platform[]>([]);
  const [minVersion, setMinVersion] = useState("");
  const [maxVersion, setMaxVersion] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null);

  const togglePlatform = (p: Platform) =>
    setPlatforms((cur) => (cur.includes(p) ? cur.filter((x) => x !== p) : [...cur, p]));

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

      // Engagement is best-effort — never block the authoring list on it.
      try {
        const eng = await fetch("/api/analytics/notifications", { credentials: "include" });
        if (eng.ok) {
          const { notifications } = (await eng.json()) as {
            notifications: NotificationEngagement[];
          };
          setStats(Object.fromEntries(notifications.map((n) => [n.id, n])));
        }
      } catch {
        /* leave stats empty */
      }
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
        body: JSON.stringify({
          title: title.trim(),
          body: body.trim(),
          level,
          category,
          platforms: platforms.length > 0 ? platforms : null,
          minVersion: minVersion.trim() || null,
          maxVersion: maxVersion.trim() || null,
        }),
      });
      if (!res.ok) {
        const b = await res.json().catch(() => ({}));
        alert(b.error || `Send failed (${res.status})`);
        return;
      }
      setTitle("");
      setBody("");
      setLevel("info");
      setCategory("general");
      setPlatforms([]);
      setMinVersion("");
      setMaxVersion("");
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
          <div className="flex flex-wrap items-center gap-4">
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
            <label className="flex items-center gap-2 text-sm text-zinc-400">
              Category
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value as Category)}
                className="bg-zinc-800 border border-zinc-700 rounded px-2 py-1 text-sm capitalize"
              >
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </label>
          </div>

          {/* Targeting (optional) — clients filter locally on platform + app version */}
          <div className="rounded border border-zinc-800 bg-zinc-900/40 p-3 space-y-2">
            <div className="text-xs font-medium text-zinc-400">Targeting (optional)</div>
            <div className="flex flex-wrap items-center gap-3">
              <span className="text-xs text-zinc-500">Platforms</span>
              {PLATFORMS.map((p) => (
                <label key={p} className="flex items-center gap-1.5 text-sm text-zinc-300">
                  <input
                    type="checkbox"
                    checked={platforms.includes(p)}
                    onChange={() => togglePlatform(p)}
                  />
                  <span className="capitalize">{p}</span>
                </label>
              ))}
              <span className="text-xs text-zinc-600">{platforms.length === 0 ? "(all)" : ""}</span>
            </div>
            <div className="flex flex-wrap items-center gap-3 text-sm text-zinc-300">
              <label className="flex items-center gap-1.5">
                <span className="text-xs text-zinc-500">Min version</span>
                <input
                  type="text"
                  value={minVersion}
                  onChange={(e) => setMinVersion(e.target.value)}
                  placeholder="e.g. 2.4.0"
                  className="w-24 bg-zinc-800 border border-zinc-700 rounded px-2 py-1 text-sm"
                />
              </label>
              <label className="flex items-center gap-1.5">
                <span className="text-xs text-zinc-500">Max version</span>
                <input
                  type="text"
                  value={maxVersion}
                  onChange={(e) => setMaxVersion(e.target.value)}
                  placeholder="e.g. 2.3.9"
                  className="w-24 bg-zinc-800 border border-zinc-700 rounded px-2 py-1 text-sm"
                />
              </label>
              <span className="text-xs text-zinc-600">(web ignores version bounds)</span>
            </div>
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={submitting || !title.trim() || !body.trim()}
              className="px-4 py-1.5 bg-deadly-red text-white rounded text-sm hover:bg-red-700 disabled:opacity-50"
            >
              {submitting ? "Sending…" : "Send"}
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
                      <div className="flex flex-wrap items-center gap-2">
                        <span className={`px-2 py-0.5 rounded text-xs font-medium ${status.cls}`}>{status.label}</span>
                        <span className="px-2 py-0.5 rounded text-xs font-medium bg-zinc-700/40 text-zinc-300 capitalize">{n.category ?? "general"}</span>
                        {n.level === "warn" && (
                          <span className="px-2 py-0.5 rounded text-xs font-medium bg-yellow-600/20 text-yellow-400">warn</span>
                        )}
                        {targetingLabel(n) && (
                          <span className="px-2 py-0.5 rounded text-xs font-medium bg-blue-600/20 text-blue-300">{targetingLabel(n)}</span>
                        )}
                        <span className="text-xs text-zinc-500">{formatTs(n.created_at)}</span>
                      </div>
                      <p className="mt-1.5 font-medium text-zinc-100 truncate">{n.title}</p>
                      <p className="mt-0.5 text-sm text-zinc-400 whitespace-pre-wrap break-words">{n.body}</p>
                      <EngagementStrip stats={stats[n.id]} />
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

/**
 * Per-notification engagement funnel (distinct clients). "Displayed" unions
 * inbox impressions and toasts; "Opened" is deliberate tap-to-read. Empty until
 * the first analytics events land for this message.
 */
function EngagementStrip({ stats }: { stats?: NotificationEngagement }) {
  if (!stats) {
    return <p className="mt-2 text-xs text-zinc-600">No engagement yet</p>;
  }
  const metrics: Array<[string, number]> = [
    ["Delivered", stats.delivered],
    ["Displayed", stats.displayed],
    ["Opened", stats.opened],
    ["Archived", stats.archived],
    ["Links", stats.link_clicks],
  ];
  return (
    <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-zinc-400">
      {metrics.map(([label, value]) => (
        <span key={label}>
          <span className="text-zinc-200 font-medium tabular-nums">{value}</span>{" "}
          <span className="text-zinc-500">{label}</span>
        </span>
      ))}
    </div>
  );
}
