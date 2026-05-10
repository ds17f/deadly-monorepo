"use client";

import Sparkline from "./Sparkline";

interface MetricCardProps {
  label: string;
  value: string | number;
  /** Singular form of the unit (rendered as plural unless value === "1"). */
  unit?: string;
  /** Tooltip on the label, for inline disambiguation. */
  hint?: string;
  timeseries?: number[];
  onClick?: () => void;
}

function computeDelta(data: number[]): { text: string; positive: boolean } | null {
  if (data.length < 8) return null;
  const recent = data.slice(-7);
  const prior = data.slice(-14, -7);
  if (prior.length === 0) return null;
  const recentAvg = recent.reduce((a, b) => a + b, 0) / recent.length;
  const priorAvg = prior.reduce((a, b) => a + b, 0) / prior.length;
  if (priorAvg === 0) return recentAvg > 0 ? { text: "+100%", positive: true } : null;
  const pct = Math.round(((recentAvg - priorAvg) / priorAvg) * 100);
  if (pct === 0) return null;
  return {
    text: `${pct > 0 ? "+" : ""}${pct}%`,
    positive: pct > 0,
  };
}

export default function MetricCard({ label, value, unit, hint, timeseries, onClick }: MetricCardProps) {
  const delta = timeseries ? computeDelta(timeseries) : null;
  const unitLabel = unit
    ? typeof value === "number"
      ? `${unit}${value === 1 ? "" : "s"}`
      : unit
    : null;

  return (
    <div
      className={`bg-deadly-surface rounded-lg p-4 ${
        onClick ? "cursor-pointer hover:bg-zinc-700/50 transition-colors active:scale-[0.98]" : ""
      }`}
      onClick={onClick}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <p
            className="text-xs text-zinc-400 uppercase tracking-wider mb-1"
            title={hint}
          >
            {label}
            {hint && <span className="ml-1 text-zinc-600 cursor-help">ⓘ</span>}
            {onClick && <span className="ml-1 text-zinc-600">&rarr;</span>}
          </p>
          <div className="flex items-baseline gap-2">
            <p className="text-2xl sm:text-3xl font-bold text-white">{value}</p>
            {unitLabel && (
              <span className="text-sm text-zinc-500">{unitLabel}</span>
            )}
            {delta && (
              <span
                className={`text-xs font-medium ${
                  delta.positive ? "text-green-400" : "text-red-400"
                }`}
              >
                {delta.text}
              </span>
            )}
          </div>
        </div>
        {timeseries && timeseries.length >= 2 && (
          <div className="flex-shrink-0 mt-2">
            <Sparkline data={timeseries} />
          </div>
        )}
      </div>
    </div>
  );
}
