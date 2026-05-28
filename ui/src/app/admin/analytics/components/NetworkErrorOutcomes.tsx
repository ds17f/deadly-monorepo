"use client";

import { useEffect, useState } from "react";

type Outcome =
  | "recover_same_track"
  | "skipped_to_next"
  | "skipped_ahead"
  | "jumped_back"
  | "next_event_is_end"
  | "no_followup";

interface OutcomeBucket {
  outcome: Outcome;
  count: number;
  avg_gap_s: number | null;
}

interface RecentError {
  ts: number;
  iid: string;
  platform: string;
  app_version: string;
  show_id: string | null;
  recording_id: string | null;
  track_index: number | null;
  outcome: Outcome;
  gap_s: number | null;
  next_track_index: number | null;
}

interface NetworkErrorSummary {
  days: number;
  platform: string | null;
  total: number;
  outcomes: OutcomeBucket[];
  recent: RecentError[];
}

interface ShowName {
  id: string;
  d: string;
  v: string;
  c: string;
  s: string;
  tc: number;
}

// Color + label per outcome. Order also drives the stacked bar.
const OUTCOME_ORDER: Outcome[] = [
  "recover_same_track",
  "skipped_to_next",
  "skipped_ahead",
  "jumped_back",
  "next_event_is_end",
  "no_followup",
];

const OUTCOME_META: Record<
  Outcome,
  { label: string; color: string; hint: string }
> = {
  recover_same_track: {
    label: "Recovered",
    color: "#10b981", // emerald
    hint: "Next event was a playback_start for the same track",
  },
  skipped_to_next: {
    label: "Skipped to next",
    color: "#f59e0b", // amber
    hint: "Autoplay advanced one track forward",
  },
  skipped_ahead: {
    label: "Skipped ahead",
    color: "#ef4444", // red
    hint: "Next start was 2+ tracks ahead — multiple tracks lost",
  },
  jumped_back: {
    label: "User went back",
    color: "#6366f1", // indigo
    hint: "User manually moved to an earlier track",
  },
  next_event_is_end: {
    label: "Another end",
    color: "#a855f7", // purple
    hint: "Next event was another playback_end (rare)",
  },
  no_followup: {
    label: "Abandoned",
    color: "#71717a", // zinc-500
    hint: "No further playback events for this iid+show",
  },
};

function formatGap(s: number | null): string {
  if (s === null) return "—";
  if (s < 60) return `${Math.round(s)}s`;
  if (s < 3600) return `${Math.round(s / 60)}m`;
  if (s < 86400) return `${(s / 3600).toFixed(1)}h`;
  return `${(s / 86400).toFixed(1)}d`;
}

function formatTime(ts: number): string {
  const d = new Date(ts);
  const now = Date.now();
  const diffM = (now - ts) / 60000;
  if (diffM < 60) return `${Math.round(diffM)}m ago`;
  if (diffM < 1440) return `${Math.round(diffM / 60)}h ago`;
  return d.toLocaleDateString();
}

export default function NetworkErrorOutcomes({
  showMap,
  onOpenInstall,
}: {
  showMap?: Map<string, ShowName>;
  onOpenInstall?: (iid: string) => void;
} = {}) {
  const [data, setData] = useState<NetworkErrorSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [platform, setPlatform] = useState<"ios" | "android" | "all">("ios");
  const [days, setDays] = useState<7 | 30 | 90>(30);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const qs = new URLSearchParams({ days: String(days), limit: "50" });
        if (platform !== "all") qs.set("platform", platform);
        const res = await fetch(`/api/analytics/network-errors?${qs}`, {
          credentials: "include",
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const body = (await res.json()) as NetworkErrorSummary;
        if (!cancelled) {
          setData(body);
          setError(null);
        }
      } catch (e: unknown) {
        if (!cancelled)
          setError(e instanceof Error ? e.message : "Failed to load");
      }
    };
    load();
    return () => {
      cancelled = true;
    };
  }, [platform, days]);

  if (error)
    return <p className="text-sm text-red-400">Network errors: {error}</p>;
  if (data === null) return <p className="text-sm text-zinc-500">Loading…</p>;

  const total = data.total;
  const byOutcome = new Map(data.outcomes.map((o) => [o.outcome, o]));

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3 text-xs">
        <div className="flex items-center gap-1">
          <span className="text-zinc-500">Platform:</span>
          {(["ios", "android", "all"] as const).map((p) => (
            <button
              key={p}
              onClick={() => setPlatform(p)}
              className={`px-2 py-1 rounded ${
                platform === p
                  ? "bg-deadly-blue text-white"
                  : "bg-zinc-800 text-zinc-300 hover:bg-zinc-700"
              }`}
            >
              {p}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-1">
          <span className="text-zinc-500">Window:</span>
          {([7, 30, 90] as const).map((d) => (
            <button
              key={d}
              onClick={() => setDays(d)}
              className={`px-2 py-1 rounded ${
                days === d
                  ? "bg-deadly-blue text-white"
                  : "bg-zinc-800 text-zinc-300 hover:bg-zinc-700"
              }`}
            >
              {d}d
            </button>
          ))}
        </div>
        <span className="ml-auto text-zinc-400 tabular-nums">
          {total} error{total === 1 ? "" : "s"} total
        </span>
      </div>

      {total === 0 ? (
        <p className="text-sm text-zinc-500 italic">
          No network_error events in this window.
        </p>
      ) : (
        <>
          {/* Stacked bar */}
          <div className="space-y-1.5">
            <div className="flex h-6 w-full rounded-md overflow-hidden bg-zinc-800">
              {OUTCOME_ORDER.map((o) => {
                const bucket = byOutcome.get(o);
                if (!bucket || bucket.count === 0) return null;
                const pct = (bucket.count / total) * 100;
                return (
                  <div
                    key={o}
                    className="h-full"
                    style={{
                      width: `${pct}%`,
                      backgroundColor: OUTCOME_META[o].color,
                    }}
                    title={`${OUTCOME_META[o].label}: ${bucket.count} (${pct.toFixed(1)}%)`}
                  />
                );
              })}
            </div>

            {/* Legend */}
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-x-4 gap-y-1 text-xs">
              {OUTCOME_ORDER.map((o) => {
                const bucket = byOutcome.get(o);
                if (!bucket) return null;
                const pct = (bucket.count / total) * 100;
                return (
                  <div
                    key={o}
                    className="flex items-center gap-2"
                    title={OUTCOME_META[o].hint}
                  >
                    <span
                      className="inline-block w-2.5 h-2.5 rounded-sm flex-shrink-0"
                      style={{ backgroundColor: OUTCOME_META[o].color }}
                    />
                    <span className="text-zinc-300">
                      {OUTCOME_META[o].label}
                    </span>
                    <span className="text-zinc-500 tabular-nums ml-auto">
                      {bucket.count} · {pct.toFixed(0)}%
                      {bucket.avg_gap_s !== null && (
                        <> · {formatGap(bucket.avg_gap_s)}</>
                      )}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Recent list */}
          {data.recent.length > 0 && (
            <div className="space-y-1">
              <p className="text-xs text-zinc-500 uppercase tracking-wide">
                Recent errors
              </p>
              <div className="bg-deadly-surface rounded-lg overflow-hidden">
                <table className="w-full text-xs">
                  <thead className="text-zinc-500">
                    <tr className="text-left">
                      <th className="px-2 py-1.5 font-normal">When</th>
                      <th className="px-2 py-1.5 font-normal">Show · track</th>
                      <th className="px-2 py-1.5 font-normal">Outcome</th>
                      <th className="px-2 py-1.5 font-normal text-right">Gap</th>
                      <th className="px-2 py-1.5 font-normal">Install</th>
                    </tr>
                  </thead>
                  <tbody className="text-zinc-300">
                    {data.recent.map((r, i) => {
                      const show = r.show_id ? showMap?.get(r.show_id) : null;
                      const showLabel = show
                        ? `${show.d} ${show.v}`
                        : (r.show_id ?? "—");
                      const meta = OUTCOME_META[r.outcome];
                      const trackLabel =
                        r.track_index !== null ? `· tr ${r.track_index}` : "";
                      const nextTrack =
                        r.next_track_index !== null && r.outcome !== "no_followup"
                          ? ` → ${r.next_track_index}`
                          : "";
                      return (
                        <tr
                          key={`${r.ts}-${i}`}
                          className="border-t border-zinc-800 hover:bg-zinc-800/40"
                        >
                          <td className="px-2 py-1.5 text-zinc-400 whitespace-nowrap">
                            {formatTime(r.ts)}
                          </td>
                          <td className="px-2 py-1.5">
                            <span className="text-zinc-200 truncate inline-block max-w-[24ch] align-middle">
                              {showLabel}
                            </span>
                            <span className="text-zinc-500">
                              {" "}
                              {trackLabel}
                              {nextTrack}
                            </span>
                          </td>
                          <td className="px-2 py-1.5">
                            <span className="inline-flex items-center gap-1.5">
                              <span
                                className="inline-block w-2 h-2 rounded-sm"
                                style={{ backgroundColor: meta.color }}
                              />
                              {meta.label}
                            </span>
                          </td>
                          <td className="px-2 py-1.5 text-right text-zinc-400 tabular-nums">
                            {formatGap(r.gap_s)}
                          </td>
                          <td className="px-2 py-1.5">
                            {onOpenInstall ? (
                              <button
                                onClick={() => onOpenInstall(r.iid)}
                                className="text-deadly-blue hover:underline"
                              >
                                {r.iid.slice(0, 8)}
                              </button>
                            ) : (
                              <span className="text-zinc-500">
                                {r.iid.slice(0, 8)}
                              </span>
                            )}
                            <span className="text-zinc-600">
                              {" "}
                              {r.platform} {r.app_version}
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
