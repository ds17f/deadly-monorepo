"use client";

import { useEffect, useState } from "react";
import { usePlatformFilter } from "./PlatformFilterContext";

interface Cohort {
  cohort_week: string;
  cohort_size: number;
  d1: number | null;
  d7: number | null;
  d30: number | null;
}

/**
 * Color a cell by its retention rate. Higher rate = brighter green.
 * Null (cohort not yet mature for this bucket) = muted slash.
 */
function CellRate({
  numerator,
  denominator,
}: {
  numerator: number | null;
  denominator: number;
}) {
  if (numerator === null) {
    return (
      <span className="text-zinc-600" title="Cohort hasn't matured for this bucket yet">
        —
      </span>
    );
  }
  const rate = denominator === 0 ? 0 : numerator / denominator;
  const pct = Math.round(rate * 100);
  // Tint background based on rate. 0% = transparent, 100% = saturated emerald.
  const intensity = Math.min(rate, 1);
  const bg = `rgba(16, 185, 129, ${0.08 + intensity * 0.55})`;
  return (
    <div
      className="rounded px-2 py-1 text-center font-mono text-sm"
      style={{ backgroundColor: bg, color: pct < 30 ? "#a1a1aa" : "#fff" }}
      title={`${numerator} of ${denominator} returned`}
    >
      {pct}%
    </div>
  );
}

export default function RetentionCohorts() {
  const { withParam, param } = usePlatformFilter();
  const [cohorts, setCohorts] = useState<Cohort[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch(withParam("/api/analytics/retention?weeks=12"), { credentials: "include" })
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = (await res.json()) as { cohorts: Cohort[] };
        setCohorts(data.cohorts);
        setError(null);
      })
      .catch((e: unknown) =>
        setError(e instanceof Error ? e.message : "Failed to load"),
      );
  }, [withParam, param]);

  if (error) {
    return <p className="text-sm text-red-400">Retention error: {error}</p>;
  }
  if (!cohorts) {
    return <p className="text-sm text-zinc-500">Loading retention…</p>;
  }
  if (cohorts.length === 0) {
    return <p className="text-sm text-zinc-500">No retention data yet.</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-xs uppercase tracking-wide text-zinc-400">
            <th className="px-2 py-1 text-left">Cohort week</th>
            <th className="px-2 py-1 text-right">Installs</th>
            <th className="px-2 py-1 text-center w-20">D1</th>
            <th className="px-2 py-1 text-center w-20">D7</th>
            <th className="px-2 py-1 text-center w-20">D30</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-zinc-800">
          {cohorts.map((c) => (
            <tr key={c.cohort_week}>
              <td className="px-2 py-1 font-mono text-xs text-zinc-300">
                {c.cohort_week}
              </td>
              <td className="px-2 py-1 text-right tabular-nums text-zinc-400">
                {c.cohort_size}
              </td>
              <td className="px-2 py-1">
                <CellRate numerator={c.d1} denominator={c.cohort_size} />
              </td>
              <td className="px-2 py-1">
                <CellRate numerator={c.d7} denominator={c.cohort_size} />
              </td>
              <td className="px-2 py-1">
                <CellRate numerator={c.d30} denominator={c.cohort_size} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
