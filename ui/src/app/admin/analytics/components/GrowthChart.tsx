"use client";

import { useEffect, useState } from "react";

interface GrowthDay {
  day: string;
  ios: number;
  android: number;
  web: number;
  total: number;
}

// Match the platform colors used by PlatformChart (ios=blue, android=green,
// web=purple) so the dashboard reads consistently.
const PLATFORM_COLORS: Record<"ios" | "android" | "web", string> = {
  ios: "bg-blue-500",
  android: "bg-green-500",
  web: "bg-purple-500",
};

const WINDOW_OPTIONS = [
  { days: 7, label: "7d" },
  { days: 30, label: "30d" },
  { days: 90, label: "90d" },
  { days: 365, label: "1y" },
] as const;

export default function GrowthChart({
  onDayClick,
}: {
  onDayClick?: (day: string) => void;
}) {
  const [days, setDays] = useState<number>(30);
  const [data, setData] = useState<GrowthDay[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<GrowthDay | null>(null);

  useEffect(() => {
    setData(null);
    fetch(`/api/analytics/growth?days=${days}`, { credentials: "include" })
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const body = (await res.json()) as { days: GrowthDay[] };
        setData(body.days);
        setError(null);
        setSelected(null);
      })
      .catch((e: unknown) =>
        setError(e instanceof Error ? e.message : "Failed to load"),
      );
  }, [days]);

  const windowPicker = (
    <div className="flex items-center gap-1 ml-auto">
      {WINDOW_OPTIONS.map((opt) => (
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
  );

  if (error)
    return (
      <div className="space-y-2">
        <div className="flex">{windowPicker}</div>
        <p className="text-sm text-red-400">Growth error: {error}</p>
      </div>
    );
  if (!data)
    return (
      <div className="space-y-2">
        <div className="flex">{windowPicker}</div>
        <p className="text-sm text-zinc-500">Loading…</p>
      </div>
    );
  if (data.length === 0)
    return (
      <div className="space-y-2">
        <div className="flex">{windowPicker}</div>
        <p className="text-sm text-zinc-500 italic">
          No installs in this window.
        </p>
      </div>
    );

  const max = Math.max(...data.map((p) => p.total), 1);
  const total = data.reduce((s, p) => s + p.total, 0);
  const peak = data.reduce(
    (best, p) => (p.total > best.total ? p : best),
    data[0],
  );

  // Pick a "round" max for the Y-axis. Bumps the chart ceiling up to the
  // next nice number so axis labels read 0/5/10 instead of 0/4.5/9.
  const niceMax = niceCeiling(max);
  const yTicks = [0, niceMax / 2, niceMax];

  return (
    <div className="space-y-3">
      <div className="flex items-baseline gap-4 text-xs text-zinc-400 flex-wrap">
        <span>
          <span className="text-zinc-200 font-semibold">
            {total.toLocaleString()}
          </span>{" "}
          new installs over {data.length} days
        </span>
        <span>
          peak{" "}
          <span className="text-zinc-200 font-semibold">{peak.total}</span> on{" "}
          {peak.day}
        </span>
        <div className="flex items-center gap-3 ml-2">
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-2.5 h-2.5 bg-blue-500 rounded-sm" />
            iOS
          </span>
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-2.5 h-2.5 bg-green-500 rounded-sm" />
            Android
          </span>
        </div>
        {windowPicker}
      </div>

      {/* Selection readout — fixed height so the chart doesn't jump when
          a bar is tapped/clicked. Empty state shows a hint. */}
      <div className="h-5 text-xs">
        {selected ? (
          <span className="text-zinc-200">
            <span className="font-mono">{selected.day}</span>
            <span className="text-zinc-500"> · </span>
            <span className="font-semibold">{selected.total}</span> total
            <span className="text-zinc-500"> · </span>
            <span className="text-blue-400">{selected.ios} iOS</span>
            <span className="text-zinc-500"> · </span>
            <span className="text-green-400">{selected.android} Android</span>
            {selected.web > 0 && (
              <>
                <span className="text-zinc-500"> · </span>
                <span className="text-purple-400">{selected.web} web</span>
              </>
            )}
          </span>
        ) : (
          <span className="text-zinc-600 italic">
            Tap a bar for details.
          </span>
        )}
      </div>

      <div className="flex gap-2">
        {/* Y-axis labels */}
        <div className="flex flex-col-reverse justify-between h-32 text-[10px] text-zinc-500 font-mono w-6 text-right">
          {yTicks.map((t) => (
            <div key={t}>{Math.round(t)}</div>
          ))}
        </div>

        {/* Chart area with horizontal gridlines */}
        <div className="flex-1 relative">
          <div className="absolute inset-0 flex flex-col-reverse justify-between pointer-events-none">
            {yTicks.map((t, i) => (
              <div
                key={t}
                className={`w-full ${i === 0 ? "" : "border-t border-zinc-800/60"}`}
              />
            ))}
          </div>
          <div className="relative flex items-end gap-[2px] h-32 bg-zinc-900/40 rounded p-2">
            {data.map((p) => {
              const totalPct = (p.total / niceMax) * 100;
              const segments = (
                [
                  { key: "ios", n: p.ios },
                  { key: "android", n: p.android },
                  { key: "web", n: p.web },
                ] as const
              ).filter((s) => s.n > 0);
              const isSelected = selected?.day === p.day;
              return (
                <button
                  key={p.day}
                  onClick={() => {
                    setSelected(p);
                    onDayClick?.(p.day);
                  }}
                  className="flex-1 group relative h-full"
                  title={`${p.day}: ${p.ios} iOS + ${p.android} Android${p.web ? ` + ${p.web} web` : ""} = ${p.total}`}
                  aria-label={`${p.day}: ${p.total} installs`}
                >
                  <div
                    className={`absolute bottom-0 left-0 right-0 flex flex-col-reverse rounded-t overflow-hidden ${isSelected ? "ring-2 ring-zinc-100" : ""}`}
                    style={{
                      height: `${totalPct}%`,
                      minHeight: p.total > 0 ? 1 : 0,
                    }}
                  >
                    {segments.map((seg) => (
                      <div
                        key={seg.key}
                        className={`${PLATFORM_COLORS[seg.key]} group-hover:brightness-110 transition-[filter] w-full`}
                        style={{ flexGrow: seg.n }}
                      />
                    ))}
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      </div>

      <div className="flex gap-[2px] pl-8 -mt-2">
        {data.map((p, i) => {
          const tickEvery = Math.max(1, Math.round(data.length / 9));
          const showTick = i % tickEvery === 0 || i === data.length - 1;
          const short = p.day.slice(5);
          return (
            <div
              key={p.day}
              className="flex-1 text-center text-[10px] text-zinc-600 font-mono"
            >
              {showTick ? short : ""}
            </div>
          );
        })}
      </div>
    </div>
  );
}

/** Round up to a nice axis ceiling — 1, 2, 5, 10, 20, 50, 100, … */
function niceCeiling(max: number): number {
  if (max <= 1) return 1;
  const pow = Math.pow(10, Math.floor(Math.log10(max)));
  const norm = max / pow;
  let nice: number;
  if (norm <= 1) nice = 1;
  else if (norm <= 2) nice = 2;
  else if (norm <= 5) nice = 5;
  else nice = 10;
  return nice * pow;
}
