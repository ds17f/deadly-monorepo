"use client";

import { useEffect, useState } from "react";
import { usePlatformFilter } from "./PlatformFilterContext";

interface Engagement {
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
}

const STAGES: Array<{ key: keyof Engagement; label: string }> = [
  { key: "delivered", label: "Delivered" },
  { key: "displayed", label: "Displayed" },
  { key: "opened", label: "Opened" },
  { key: "archived", label: "Archived" },
];

// Per-notification engagement funnel (distinct clients) for the analytics
// dashboard. Counts come from /api/analytics/notifications; titles are joined
// from /api/admin/notifications. "Displayed" unions inbox impressions + toasts;
// "Opened" is deliberate tap-to-read.
export default function NotificationEngagement() {
  const { withParam, param } = usePlatformFilter();
  const [rows, setRows] = useState<Engagement[] | null>(null);
  const [titles, setTitles] = useState<Record<number, string>>({});
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setRows(null);
    Promise.all([
      fetch(withParam("/api/analytics/notifications?days=90"), { credentials: "include" }).then(
        async (r) => {
          if (!r.ok) throw new Error(`HTTP ${r.status}`);
          return (await r.json()) as { notifications: Engagement[] };
        },
      ),
      fetch("/api/admin/notifications", { credentials: "include" })
        .then((r) => (r.ok ? r.json() : { notifications: [] }))
        .catch(() => ({ notifications: [] })) as Promise<{ notifications: AdminNotification[] }>,
    ])
      .then(([eng, admin]) => {
        setRows(eng.notifications);
        setTitles(Object.fromEntries(admin.notifications.map((n) => [n.id, n.title])));
        setError(null);
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : "Failed to load"));
  }, [withParam, param]);

  if (error) return <p className="text-sm text-red-400">{error}</p>;
  if (!rows) return <p className="text-sm text-zinc-500">Loading…</p>;
  if (rows.length === 0)
    return <p className="text-sm text-zinc-500">No notification engagement yet.</p>;

  return (
    <div className="bg-deadly-surface rounded-lg p-4 space-y-4">
      {rows.map((r) => {
        // Funnel bars are relative to "delivered" — the top of the funnel.
        const max = Math.max(r.delivered, 1);
        return (
          <div key={r.id} className="space-y-1.5">
            <div className="flex items-baseline justify-between gap-2">
              <span className="text-sm text-zinc-200 truncate">
                {titles[r.id] ?? `Notification #${r.id}`}
              </span>
              {r.link_clicks > 0 && (
                <span className="shrink-0 text-xs text-zinc-500 tabular-nums">
                  {r.link_clicks} link {r.link_clicks === 1 ? "click" : "clicks"}
                </span>
              )}
            </div>
            <div className="grid grid-cols-[auto_1fr_auto] items-center gap-x-3 gap-y-1">
              {STAGES.map(({ key, label }) => {
                const value = r[key] as number;
                return (
                  <div key={key} className="contents">
                    <span className="text-xs text-zinc-500 w-16">{label}</span>
                    <div className="bg-zinc-800 rounded-full h-3 overflow-hidden">
                      <div
                        className="bg-deadly-accent h-full rounded-full transition-all"
                        style={{ width: `${(value / max) * 100}%` }}
                      />
                    </div>
                    <span className="text-xs text-zinc-400 w-10 text-right tabular-nums">
                      {value}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
