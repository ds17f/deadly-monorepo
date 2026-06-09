"use client";

import { useEffect, useMemo, useState } from "react";
import { PLATFORMS, type PlatformId } from "./PlatformFilterContext";

interface VersionDistributionRow {
  version: string;
  active: number;
  idle: number;
  stale: number;
  total: number;
}

interface VersionDay {
  day: string;
  version: string;
  count: number;
}

type View = "snapshot" | "overtime";
type Granularity = "point" | "minor";

// How many version bands view B keeps before folding the rest into a muted
// "older" bucket. Kept by recency (newest semver first), not by volume — the
// point of an adoption chart is watching the latest releases climb, and those
// start out low-volume. Keep in step with BAND_PALETTE's length.
const MAX_BANDS = 12;
const OLDER = "older";

const WINDOW_OPTIONS = [
  { days: 30, label: "30d" },
  { days: 90, label: "90d" },
  { days: 180, label: "180d" },
  { days: 365, label: "1y" },
] as const;

/** "2.31.1" → [2,31,1]; non-semver (e.g. "dev") → null so it sorts last. */
function parseSemver(v: string): [number, number, number] | null {
  const m = /^(\d+)\.(\d+)(?:\.(\d+))?/.exec(v);
  if (!m) return null;
  return [Number(m[1]), Number(m[2]), Number(m[3] ?? 0)];
}

/** Descending semver compare; unparseable versions sort to the bottom. */
function compareVersionsDesc(a: string, b: string): number {
  const pa = parseSemver(a);
  const pb = parseSemver(b);
  if (pa && pb) {
    for (let i = 0; i < 3; i++) if (pb[i] !== pa[i]) return pb[i] - pa[i];
    return a.localeCompare(b);
  }
  if (pa) return -1; // parseable before unparseable
  if (pb) return 1;
  return a.localeCompare(b);
}

/** Collapse a version to its label at the chosen granularity. */
function collapse(v: string, g: Granularity): string {
  if (g === "minor") {
    const m = /^(\d+)\.(\d+)/.exec(v);
    if (m) return `${m[1]}.${m[2]}`;
  }
  return v;
}

// Distinct, well-separated hues so adjacent version bands are actually
// tellable apart (a single-hue fade made the stack unreadable). Assigned by
// semver-desc rank so the newest version is always the leading blue. The
// folded "older" bucket is a flat gray.
const BAND_PALETTE = [
  "hsl(212, 85%, 58%)", // blue   — newest
  "hsl(160, 68%, 44%)", // emerald
  "hsl(38, 92%, 55%)", // amber
  "hsl(280, 60%, 64%)", // violet
  "hsl(345, 78%, 60%)", // rose
  "hsl(190, 80%, 48%)", // cyan
  "hsl(24, 88%, 56%)", // orange
  "hsl(95, 52%, 50%)", // lime
  "hsl(320, 65%, 62%)", // magenta
  "hsl(130, 55%, 45%)", // green
  "hsl(52, 90%, 55%)", // yellow
  "hsl(250, 65%, 66%)", // indigo
];
const OLDER_COLOR = "hsl(220, 8%, 42%)";

function bandColor(version: string, rank: number): string {
  if (version === OLDER) return OLDER_COLOR;
  return BAND_PALETTE[rank % BAND_PALETTE.length];
}

export default function VersionPanel() {
  // iOS and Android version on independent release trains, so the same string
  // ("2.34.0") means different builds on each — summing them is meaningless.
  // This panel therefore owns a single-select platform picker and ignores the
  // dashboard's (multi-select) global filter on purpose.
  const [platform, setPlatform] = useState<PlatformId>("ios");
  const [view, setView] = useState<View>("snapshot");
  const [granularity, setGranularity] = useState<Granularity>("point");
  const [days, setDays] = useState<number>(90);

  const [dist, setDist] = useState<VersionDistributionRow[] | null>(null);
  const [series, setSeries] = useState<VersionDay[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Snapshot (view A) — refetched on platform change.
  useEffect(() => {
    setDist(null);
    fetch(`/api/analytics/versions?platforms=${platform}`, {
      credentials: "include",
    })
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const body = (await res.json()) as { versions: VersionDistributionRow[] };
        setDist(body.versions);
        setError(null);
      })
      .catch((e: unknown) =>
        setError(e instanceof Error ? e.message : "Failed to load"),
      );
  }, [platform]);

  // Over-time (view B) — refetched on window or platform change.
  useEffect(() => {
    setSeries(null);
    fetch(
      `/api/analytics/version-timeseries?days=${days}&platforms=${platform}`,
      { credentials: "include" },
    )
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const body = (await res.json()) as { days: VersionDay[] };
        setSeries(body.days);
        setError(null);
      })
      .catch((e: unknown) =>
        setError(e instanceof Error ? e.message : "Failed to load"),
      );
  }, [days, platform]);

  // ── View A: collapse + semver order ────────────────────────────────
  const snapshotRows = useMemo(() => {
    if (!dist) return null;
    const byVersion = new Map<string, VersionDistributionRow>();
    for (const r of dist) {
      const v = collapse(r.version, granularity);
      const acc = byVersion.get(v) ?? {
        version: v,
        active: 0,
        idle: 0,
        stale: 0,
        total: 0,
      };
      acc.active += r.active;
      acc.idle += r.idle;
      acc.stale += r.stale;
      acc.total += r.total;
      byVersion.set(v, acc);
    }
    return Array.from(byVersion.values()).sort((a, b) =>
      compareVersionsDesc(a.version, b.version),
    );
  }, [dist, granularity]);

  // ── View B: collapse, fold to top bands, normalize to share ─────────
  const overtime = useMemo(() => {
    if (!series) return null;

    // Aggregate counts by (day, collapsed version).
    const days = new Map<string, Map<string, number>>();
    const totals = new Map<string, number>(); // version → total over window
    for (const r of series) {
      const v = collapse(r.version, granularity);
      const day = days.get(r.day) ?? new Map<string, number>();
      day.set(v, (day.get(v) ?? 0) + r.count);
      days.set(r.day, day);
      totals.set(v, (totals.get(v) ?? 0) + r.count);
    }

    // Keep the MAX_BANDS *newest* versions (semver-desc) and fold everything
    // older into "older" — so a brand-new, low-volume release still gets its
    // own band to watch climb, instead of being culled for being small.
    const order = Array.from(totals.keys())
      .sort(compareVersionsDesc)
      .slice(0, MAX_BANDS);
    const keptSet = new Set(order);
    if (totals.size > keptSet.size) order.push(OLDER);

    const colors = new Map<string, string>();
    order.forEach((v, i) => colors.set(v, bandColor(v, i)));

    const points = Array.from(days.entries())
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([day, counts]) => {
        const folded = new Map<string, number>();
        let total = 0;
        for (const [v, c] of counts) {
          const key = keptSet.has(v) ? v : OLDER;
          folded.set(key, (folded.get(key) ?? 0) + c);
          total += c;
        }
        return { day, counts: folded, total };
      });

    return { order, colors, points };
  }, [series, granularity]);

  // ── Shared header controls ─────────────────────────────────────────
  const platformToggle = (
    <div className="flex items-center gap-1">
      {/* Web omitted — version numbers only meaningful for the installed apps. */}
      {PLATFORMS.filter((p) => p.id !== "web").map((p) => (
        <button
          key={p.id}
          onClick={() => setPlatform(p.id)}
          className={`px-2 py-0.5 text-xs rounded ${
            platform === p.id
              ? "bg-deadly-blue text-white font-semibold"
              : "bg-zinc-800 text-zinc-400 hover:text-zinc-200"
          }`}
        >
          {p.label}
        </button>
      ))}
    </div>
  );

  const viewToggle = (
    <div className="flex items-center gap-1">
      {(
        [
          ["snapshot", "By version"],
          ["overtime", "Over time"],
        ] as const
      ).map(([v, label]) => (
        <button
          key={v}
          onClick={() => setView(v)}
          className={`px-2 py-0.5 text-xs rounded ${
            view === v
              ? "bg-zinc-200 text-zinc-900 font-semibold"
              : "bg-zinc-800 text-zinc-400 hover:text-zinc-200"
          }`}
        >
          {label}
        </button>
      ))}
    </div>
  );

  const granularityToggle = (
    <button
      onClick={() =>
        setGranularity((g) => (g === "point" ? "minor" : "point"))
      }
      className="px-2 py-0.5 text-xs rounded bg-zinc-800 text-zinc-400 hover:text-zinc-200"
      title="Toggle between full point releases (2.34.0) and minor versions (2.34.x)"
    >
      {granularity === "point" ? "2.x.x" : "2.x"}
    </button>
  );

  const header = (
    <div className="flex items-center gap-2 flex-wrap">
      {platformToggle}
      <span className="text-zinc-700">·</span>
      {viewToggle}
      <div className="ml-auto flex items-center gap-1">
        {granularityToggle}
        {view === "overtime" &&
          WINDOW_OPTIONS.map((opt) => (
            <button
              key={opt.days}
              onClick={() => setDays(opt.days)}
              className={`px-2 py-0.5 text-xs rounded ${
                days === opt.days
                  ? "bg-zinc-200 text-zinc-900 font-semibold"
                  : "bg-zinc-800 text-zinc-400 hover:text-zinc-200"
              }`}
            >
              {opt.label}
            </button>
          ))}
      </div>
    </div>
  );

  if (error)
    return (
      <div className="space-y-3">
        {header}
        <p className="text-sm text-red-400">Versions error: {error}</p>
      </div>
    );

  return (
    <div className="space-y-3">
      {header}
      {view === "snapshot" ? (
        <SnapshotView rows={snapshotRows} />
      ) : (
        <OverTimeView data={overtime} />
      )}
    </div>
  );
}

// ── View A: horizontal recency-stacked bars ──────────────────────────

const SEG_COLORS = {
  active: "bg-emerald-500",
  idle: "bg-amber-500",
  stale: "bg-zinc-600",
} as const;

function SnapshotView({ rows }: { rows: VersionDistributionRow[] | null }) {
  if (!rows) return <p className="text-sm text-zinc-500">Loading…</p>;
  if (rows.length === 0)
    return <p className="text-sm text-zinc-500 italic">No installs.</p>;

  const maxTotal = Math.max(...rows.map((r) => r.total), 1);
  const totals = rows.reduce(
    (s, r) => ({
      active: s.active + r.active,
      idle: s.idle + r.idle,
      stale: s.stale + r.stale,
    }),
    { active: 0, idle: 0, stale: 0 },
  );

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3 text-xs text-zinc-400 flex-wrap">
        <Legend swatch="bg-emerald-500" label={`active ≤7d (${totals.active})`} />
        <Legend swatch="bg-amber-500" label={`idle 8–30d (${totals.idle})`} />
        <Legend swatch="bg-zinc-600" label={`stale >30d (${totals.stale})`} />
      </div>

      <div className="space-y-1.5">
        {rows.map((r) => {
          const segments = (
            [
              { key: "active", n: r.active },
              { key: "idle", n: r.idle },
              { key: "stale", n: r.stale },
            ] as const
          ).filter((s) => s.n > 0);
          return (
            <div key={r.version} className="flex items-center gap-2">
              <span className="w-14 shrink-0 text-right font-mono text-xs text-zinc-300 tabular-nums">
                {r.version}
              </span>
              <div className="flex-1 h-4 rounded-sm bg-zinc-900/40 overflow-hidden">
                <div
                  className="flex h-full rounded-sm overflow-hidden"
                  style={{ width: `${(r.total / maxTotal) * 100}%` }}
                  title={`${r.version}: ${r.active} active · ${r.idle} idle · ${r.stale} stale`}
                >
                  {segments.map((seg) => (
                    <div
                      key={seg.key}
                      className={SEG_COLORS[seg.key]}
                      style={{ flexGrow: seg.n }}
                    />
                  ))}
                </div>
              </div>
              <span className="w-10 shrink-0 text-right text-xs text-zinc-400 tabular-nums">
                {r.total}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ── View B: normalized stacked-area version mix over time ────────────

interface OverTimeData {
  order: string[];
  colors: Map<string, string>;
  points: Array<{ day: string; counts: Map<string, number>; total: number }>;
}

function formatDay(day: string): string {
  const d = new Date(day + "T00:00:00");
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function OverTimeView({ data }: { data: OverTimeData | null }) {
  // Tap a day → pin its breakdown in the readout. Default to the most recent
  // day so the panel opens showing the current mix instead of an empty prompt.
  const [selectedDay, setSelectedDay] = useState<string | null>(null);
  // Tap a legend version → spotlight its bands across every day; the rest fade.
  const [focusedVersion, setFocusedVersion] = useState<string | null>(null);

  const { order, colors, points } = data ?? {
    order: [],
    colors: new Map<string, string>(),
    points: [],
  };

  const selected = useMemo(() => {
    if (points.length === 0) return null;
    const day =
      (selectedDay && points.find((p) => p.day === selectedDay)) ||
      points[points.length - 1];
    return day;
  }, [points, selectedDay]);

  if (!data) return <p className="text-sm text-zinc-500">Loading…</p>;
  if (points.length === 0)
    return (
      <p className="text-sm text-zinc-500 italic">
        No active installs in this window.
      </p>
    );

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-x-2 gap-y-1 text-xs flex-wrap">
        {order.map((v) => {
          const isFocused = focusedVersion === v;
          const dimmed = focusedVersion != null && !isFocused;
          return (
            <button
              key={v}
              onClick={() => setFocusedVersion(isFocused ? null : v)}
              title="Tap to spotlight this version across all days"
              className={`flex items-center gap-1.5 rounded px-1 py-0.5 transition-opacity ${
                isFocused
                  ? "bg-zinc-800 text-zinc-100 font-medium"
                  : "text-zinc-400 hover:text-zinc-200"
              } ${dimmed ? "opacity-40" : ""}`}
            >
              <span
                className="inline-block w-2.5 h-2.5 rounded-sm"
                style={{ backgroundColor: colors.get(v) }}
              />
              {v}
            </button>
          );
        })}
      </div>

      {/* Breakdown readout for the tapped (or latest) day — the primary way to
          read exact numbers, since per-day bars are too thin to label. */}
      {selected && (
        <div className="rounded bg-zinc-900/60 p-2.5">
          <div className="mb-1.5 flex items-baseline justify-between">
            <span className="text-sm font-medium text-zinc-200">
              {formatDay(selected.day)}
            </span>
            <span className="text-xs text-zinc-500 tabular-nums">
              {selected.total} active
            </span>
          </div>
          <div className="grid grid-cols-2 gap-x-4 gap-y-1 sm:grid-cols-3">
            {order
              .filter((v) => (selected.counts.get(v) ?? 0) > 0)
              .map((v) => {
                const c = selected.counts.get(v) ?? 0;
                const pct = selected.total ? Math.round((c / selected.total) * 100) : 0;
                const faded = focusedVersion != null && focusedVersion !== v;
                return (
                  <button
                    key={v}
                    onClick={() => setFocusedVersion(focusedVersion === v ? null : v)}
                    className={`flex items-center gap-1.5 text-xs text-left transition-opacity ${
                      faded ? "opacity-40" : ""
                    }`}
                  >
                    <span
                      className="inline-block w-2.5 h-2.5 shrink-0 rounded-sm"
                      style={{ backgroundColor: colors.get(v) }}
                    />
                    <span
                      className={
                        focusedVersion === v
                          ? "font-medium text-zinc-100"
                          : "text-zinc-300"
                      }
                    >
                      {v}
                    </span>
                    <span className="ml-auto text-zinc-500 tabular-nums">
                      {pct}% ({c})
                    </span>
                  </button>
                );
              })}
          </div>
        </div>
      )}

      {/* 100%-normalized stack: each day is a full-height bar split by the
          version share of installs active that day. Tap to pin the breakdown. */}
      <div className="flex items-stretch gap-px h-40 bg-zinc-900/40 rounded p-2">
        {points.map((p) => {
          const isSelected = selected?.day === p.day;
          return (
            <button
              key={p.day}
              onClick={() => setSelectedDay(p.day)}
              aria-label={`${p.day}: ${p.total} active installs`}
              className={`flex-1 h-full flex flex-col rounded-sm overflow-hidden min-w-[3px] transition-opacity ${
                isSelected ? "ring-2 ring-zinc-100 ring-inset" : ""
              } ${selected && !isSelected && !focusedVersion ? "opacity-70 hover:opacity-100" : ""}`}
            >
              {p.total === 0
                ? null
                : order.map((v) => {
                    const c = p.counts.get(v) ?? 0;
                    if (c === 0) return null;
                    const faded = focusedVersion != null && focusedVersion !== v;
                    return (
                      <div
                        key={v}
                        className="transition-opacity"
                        style={{
                          flexGrow: c,
                          backgroundColor: colors.get(v),
                          opacity: faded ? 0.12 : 1,
                        }}
                      />
                    );
                  })}
            </button>
          );
        })}
      </div>

      {/* X-axis: a handful of date ticks. */}
      <div className="flex gap-px -mt-1">
        {points.map((p, i) => {
          const every = Math.max(1, Math.round(points.length / 9));
          const show = i % every === 0 || i === points.length - 1;
          return (
            <div
              key={p.day}
              className="flex-1 text-center text-[10px] text-zinc-600 font-mono"
            >
              {show ? p.day.slice(5) : ""}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Legend({ swatch, label }: { swatch: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className={`inline-block w-2.5 h-2.5 rounded-sm ${swatch}`} />
      {label}
    </span>
  );
}
