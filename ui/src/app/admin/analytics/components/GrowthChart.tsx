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

export default function GrowthChart({
  onDayClick,
}: {
  onDayClick?: (day: string) => void;
}) {
  const [data, setData] = useState<GrowthDay[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch("/api/analytics/growth?days=60", { credentials: "include" })
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const body = (await res.json()) as { days: GrowthDay[] };
        setData(body.days);
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

  const max = Math.max(...data.map((p) => p.total));
  const total = data.reduce((s, p) => s + p.total, 0);
  const peak = data.reduce(
    (best, p) => (p.total > best.total ? p : best),
    data[0],
  );

  return (
    <div className="space-y-3">
      <div className="flex items-baseline gap-6 text-xs text-zinc-400 flex-wrap">
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
        <div className="flex items-center gap-3 ml-auto">
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-2.5 h-2.5 bg-blue-500 rounded-sm" />
            iOS
          </span>
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-2.5 h-2.5 bg-green-500 rounded-sm" />
            Android
          </span>
        </div>
      </div>

      <div className="flex items-end gap-[2px] h-32 bg-zinc-900/40 rounded p-2">
        {data.map((p) => {
          const totalPct = max === 0 ? 0 : (p.total / max) * 100;
          // Within a single bar, slice the height proportionally between
          // the platforms so iOS sits on top of Android (and Android on
          // top of web if present).
          const segments = (
            [
              { key: "ios", n: p.ios },
              { key: "android", n: p.android },
              { key: "web", n: p.web },
            ] as const
          ).filter((s) => s.n > 0);
          return (
            <button
              key={p.day}
              onClick={() => onDayClick?.(p.day)}
              className="flex-1 group relative h-full"
              title={`${p.day}: ${p.ios} iOS + ${p.android} Android${p.web ? ` + ${p.web} web` : ""} = ${p.total}`}
            >
              <div className="absolute bottom-0 left-0 right-0 flex flex-col-reverse rounded-t overflow-hidden"
                   style={{ height: `${totalPct}%`, minHeight: p.total > 0 ? 1 : 0 }}>
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

      <div className="flex gap-[2px] px-1 -mt-2">
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
