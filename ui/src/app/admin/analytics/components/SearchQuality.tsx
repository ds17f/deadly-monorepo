"use client";

import { useEffect, useState } from "react";
import { usePlatformFilter } from "./PlatformFilterContext";

interface SearchQuality {
  total_searches: number;
  zero_result_count: number;
  abandon_count: number;
  median_selected_index: number | null;
  top_zero_result: Array<{ query: string; count: number }>;
  top_successful: Array<{ query: string; count: number }>;
}

function pct(num: number, denom: number): string {
  if (denom === 0) return "—";
  return `${Math.round((num / denom) * 100)}%`;
}

function StatCard({
  label,
  value,
  subtitle,
}: {
  label: string;
  value: string | number;
  subtitle?: string;
}) {
  return (
    <div className="bg-deadly-surface rounded-lg p-3">
      <p className="text-xs text-zinc-400 uppercase tracking-wider">{label}</p>
      <p className="text-2xl font-semibold text-zinc-100 mt-1">{value}</p>
      {subtitle && <p className="text-xs text-zinc-500 mt-0.5">{subtitle}</p>}
    </div>
  );
}

function QueryList({
  rows,
  emptyMessage,
}: {
  rows: Array<{ query: string; count: number }>;
  emptyMessage: string;
}) {
  if (rows.length === 0) {
    return <p className="text-sm text-zinc-500 italic">{emptyMessage}</p>;
  }
  return (
    <ol className="space-y-1">
      {rows.map((r, i) => (
        <li
          key={`${r.query}-${i}`}
          className="flex items-baseline gap-3 text-sm bg-zinc-900/40 rounded px-2 py-1"
        >
          <span className="text-zinc-600 font-mono text-xs w-6 text-right tabular-nums">
            {i + 1}
          </span>
          <span className="text-zinc-200 truncate flex-1">&ldquo;{r.query}&rdquo;</span>
          <span className="text-zinc-400 tabular-nums text-xs">
            {r.count}×
          </span>
        </li>
      ))}
    </ol>
  );
}

export default function SearchQuality() {
  const { withParam, param } = usePlatformFilter();
  const [data, setData] = useState<SearchQuality | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch(withParam("/api/analytics/search-quality?days=30"), { credentials: "include" })
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const body = (await res.json()) as SearchQuality;
        setData(body);
        setError(null);
      })
      .catch((e: unknown) =>
        setError(e instanceof Error ? e.message : "Failed to load"),
      );
  }, [withParam, param]);

  if (error)
    return <p className="text-sm text-red-400">Search quality error: {error}</p>;
  if (!data) return <p className="text-sm text-zinc-500">Loading…</p>;

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <StatCard label="Searches (30d)" value={data.total_searches} />
        <StatCard
          label="Zero results"
          value={pct(data.zero_result_count, data.total_searches)}
          subtitle={`${data.zero_result_count} searches`}
        />
        <StatCard
          label="Abandoned"
          value={pct(data.abandon_count, data.total_searches)}
          subtitle="No result tapped"
        />
        <StatCard
          label="Median rank"
          value={
            data.median_selected_index != null
              ? `#${data.median_selected_index + 1}`
              : "—"
          }
          subtitle={
            data.median_selected_index != null
              ? "Lower is better"
              : "Needs selected_index data"
          }
        />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <h3 className="text-sm font-medium text-zinc-300 mb-2">
            Top zero-result queries
          </h3>
          <QueryList
            rows={data.top_zero_result}
            emptyMessage="No zero-result searches in the window."
          />
        </div>
        <div>
          <h3 className="text-sm font-medium text-zinc-300 mb-2">
            Top successful queries
          </h3>
          <QueryList
            rows={data.top_successful}
            emptyMessage="No successful searches in the window."
          />
        </div>
      </div>
    </div>
  );
}
