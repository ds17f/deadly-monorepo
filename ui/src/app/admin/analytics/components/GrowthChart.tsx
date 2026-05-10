"use client";

import { useEffect, useState } from "react";

interface DayPoint {
  day: string;
  value: number;
}

export default function GrowthChart({
  onDayClick,
}: {
  onDayClick?: (day: string) => void;
}) {
  const [data, setData] = useState<DayPoint[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch("/api/analytics/timeseries?metric=new_installs&days=60", {
      credentials: "include",
    })
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const body = (await res.json()) as DayPoint[];
        setData(body);
        setError(null);
      })
      .catch((e: unknown) =>
        setError(e instanceof Error ? e.message : "Failed to load"),
      );
  }, []);

  if (error)
    return <p className="text-sm text-red-400">Growth error: {error}</p>;
  if (!data) return <p className="text-sm text-zinc-500">Loading…</p>;
  if (data.length === 0)
    return <p className="text-sm text-zinc-500 italic">No installs yet.</p>;

  const max = Math.max(...data.map((p) => p.value));
  const total = data.reduce((s, p) => s + p.value, 0);
  const peak = data.reduce((best, p) => (p.value > best.value ? p : best), data[0]);

  return (
    <div className="space-y-3">
      <div className="flex items-baseline gap-6 text-xs text-zinc-400">
        <span>
          <span className="text-zinc-200 font-semibold">{total.toLocaleString()}</span>{" "}
          new installs over {data.length} days
        </span>
        <span>
          peak{" "}
          <span className="text-zinc-200 font-semibold">{peak.value}</span> on{" "}
          {peak.day}
        </span>
      </div>
      <div className="flex items-end gap-[2px] h-32 bg-zinc-900/40 rounded p-2">
        {data.map((p) => {
          const heightPct = max === 0 ? 0 : (p.value / max) * 100;
          return (
            <button
              key={p.day}
              onClick={() => onDayClick?.(p.day)}
              className="flex-1 group relative"
              title={`${p.day}: ${p.value} install${p.value !== 1 ? "s" : ""}`}
              style={{ height: "100%" }}
            >
              <div className="absolute bottom-0 left-0 right-0 flex items-end h-full">
                <div
                  className="w-full bg-deadly-blue/70 group-hover:bg-deadly-blue rounded-t transition-colors"
                  style={{ height: `${heightPct}%`, minHeight: p.value > 0 ? 1 : 0 }}
                />
              </div>
            </button>
          );
        })}
      </div>
      <div className="flex justify-between text-[10px] text-zinc-600 font-mono px-1">
        <span>{data[0]?.day}</span>
        <span>{data[data.length - 1]?.day}</span>
      </div>
    </div>
  );
}
