"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect, useState, useCallback, useMemo } from "react";

interface AnalyticsSummary {
  dau: number;
  wau: number;
  mau: number;
  total_installs: number;
  stale_installs_30d: number;
  platform_split: Record<string, number>;
  top_shows: Array<{ show_id: string; plays: number }>;
  feature_adoption: Record<string, number>;
  avg_completion_rate: number | null;
  events_today: number;
}

interface DetailRow {
  iid: string;
  platform: string;
  app_version: string;
  last_seen: string;
  event_count: number;
  detail?: string;
}

type SortDir = "asc" | "desc";
type DetailMetric =
  | "dau" | "wau" | "mau"
  | "total_installs" | "stale_installs"
  | "events_today"
  | "top_shows"
  | "feature_adoption"
  | "platform_split"
  | "playback";

const METRIC_LABELS: Record<DetailMetric, string> = {
  dau: "Daily Active Users",
  wau: "Weekly Active Users",
  mau: "Monthly Active Users",
  total_installs: "Total Installs",
  stale_installs: "Stale Installs (30d)",
  events_today: "Events Today",
  top_shows: "Top Shows (30d)",
  feature_adoption: "Feature Adoption (30d)",
  platform_split: "Platform Split (30d)",
  playback: "Playback (30d)",
};

const REFRESH_INTERVAL = 30_000;

export default function AnalyticsDashboard() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const [data, setData] = useState<AnalyticsSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [activeMetric, setActiveMetric] = useState<DetailMetric | null>(null);
  const [detailRows, setDetailRows] = useState<DetailRow[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [sortKey, setSortKey] = useState<keyof DetailRow>("last_seen");
  const [sortDir, setSortDir] = useState<SortDir>("desc");

  const fetchSummary = useCallback(async () => {
    try {
      const res = await fetch("/api/analytics/summary", {
        credentials: "include",
      });
      if (res.status === 403) {
        setError("Forbidden");
        return;
      }
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      setData(json);
      setError(null);
      setLastUpdated(new Date());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load");
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchDetail = useCallback(async (metric: DetailMetric) => {
    setDetailLoading(true);
    try {
      const res = await fetch(`/api/analytics/detail?metric=${metric}`, {
        credentials: "include",
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const rows = await res.json();
      setDetailRows(rows);
    } catch {
      setDetailRows([]);
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const openDetail = useCallback(
    (metric: DetailMetric) => {
      setActiveMetric(metric);
      setSortKey("last_seen");
      setSortDir("desc");
      fetchDetail(metric);
    },
    [fetchDetail],
  );

  const closeDetail = useCallback(() => {
    setActiveMetric(null);
    setDetailRows([]);
  }, []);

  // Auto-refresh summary
  useEffect(() => {
    if (authLoading) return;
    if (!user?.isAdmin) {
      router.replace("/");
      return;
    }
    fetchSummary();
    const interval = setInterval(fetchSummary, REFRESH_INTERVAL);
    return () => clearInterval(interval);
  }, [authLoading, user?.isAdmin, router, fetchSummary]);

  // Auto-refresh detail when open
  useEffect(() => {
    if (!activeMetric) return;
    const interval = setInterval(() => fetchDetail(activeMetric), REFRESH_INTERVAL);
    return () => clearInterval(interval);
  }, [activeMetric, fetchDetail]);

  const sortedDetail = useMemo(() => {
    const rows = [...detailRows];
    rows.sort((a, b) => {
      const aVal = a[sortKey] ?? "";
      const bVal = b[sortKey] ?? "";
      if (typeof aVal === "number" && typeof bVal === "number") {
        return sortDir === "asc" ? aVal - bVal : bVal - aVal;
      }
      const cmp = String(aVal).localeCompare(String(bVal));
      return sortDir === "asc" ? cmp : -cmp;
    });
    return rows;
  }, [detailRows, sortKey, sortDir]);

  const toggleSort = (key: keyof DetailRow) => {
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("desc");
    }
  };

  const sortIndicator = (key: keyof DetailRow) =>
    sortKey === key ? (sortDir === "asc" ? " ▲" : " ▼") : "";

  if (authLoading || (!user?.isAdmin && !error)) {
    return (
      <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
        <p className="text-zinc-400">Loading...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
        <p className="text-deadly-red">{error}</p>
      </div>
    );
  }

  if (loading || !data) {
    return (
      <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
        <p className="text-zinc-400">Loading analytics...</p>
      </div>
    );
  }

  const maxFeatureUses = Math.max(...Object.values(data.feature_adoption), 1);
  const totalPlatform = Object.values(data.platform_split).reduce(
    (a, b) => a + b,
    0,
  );

  return (
    <div className="min-h-screen bg-deadly-bg p-6 max-w-5xl mx-auto">
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-2xl font-bold text-deadly-red">Analytics</h1>
        <div className="flex items-center gap-3">
          <span className="text-xs text-zinc-600">
            auto-refresh {REFRESH_INTERVAL / 1000}s
          </span>
          {lastUpdated && (
            <span className="text-xs text-zinc-500">
              Updated {lastUpdated.toLocaleTimeString()}
            </span>
          )}
        </div>
      </div>

      {/* Active Users */}
      <section className="mb-8">
        <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3">
          Active Users
        </h2>
        <div className="grid grid-cols-3 gap-4">
          <MetricCard label="DAU" value={data.dau} onClick={() => openDetail("dau")} />
          <MetricCard label="WAU" value={data.wau} onClick={() => openDetail("wau")} />
          <MetricCard label="MAU" value={data.mau} onClick={() => openDetail("mau")} />
        </div>
      </section>

      {/* Install Base */}
      <section className="mb-8">
        <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3">
          Install Base
        </h2>
        <div className="grid grid-cols-3 gap-4">
          <MetricCard
            label="Total Installs"
            value={data.total_installs}
            onClick={() => openDetail("total_installs")}
          />
          <MetricCard
            label="Stale (30d)"
            value={data.stale_installs_30d}
            onClick={() => openDetail("stale_installs")}
          />
          <MetricCard
            label="Events Today"
            value={data.events_today}
            onClick={() => openDetail("events_today")}
          />
        </div>
      </section>

      {/* Platform Split */}
      {totalPlatform > 0 && (
        <section className="mb-8">
          <h2
            className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3 cursor-pointer hover:text-zinc-200 transition-colors"
            onClick={() => openDetail("platform_split")}
          >
            Platform Split (30d) →
          </h2>
          <div className="bg-deadly-surface rounded-lg p-4">
            {Object.entries(data.platform_split).map(([platform, count]) => (
              <div key={platform} className="flex items-center gap-3 mb-2 last:mb-0">
                <span className="text-sm text-zinc-300 w-20">{platform}</span>
                <div className="flex-1 bg-zinc-800 rounded-full h-5 overflow-hidden">
                  <div
                    className="bg-deadly-blue h-full rounded-full transition-all"
                    style={{
                      width: `${(count / totalPlatform) * 100}%`,
                    }}
                  />
                </div>
                <span className="text-sm text-zinc-400 w-16 text-right">
                  {count} ({Math.round((count / totalPlatform) * 100)}%)
                </span>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Top Shows */}
      {data.top_shows.length > 0 && (
        <section className="mb-8">
          <h2
            className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3 cursor-pointer hover:text-zinc-200 transition-colors"
            onClick={() => openDetail("top_shows")}
          >
            Top Shows (30d) →
          </h2>
          <div className="bg-deadly-surface rounded-lg overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-zinc-700">
                  <th className="text-left text-zinc-400 px-4 py-2">#</th>
                  <th className="text-left text-zinc-400 px-4 py-2">Show</th>
                  <th className="text-right text-zinc-400 px-4 py-2">Plays</th>
                </tr>
              </thead>
              <tbody>
                {data.top_shows.map((show, i) => (
                  <tr
                    key={show.show_id}
                    className="border-b border-zinc-800 last:border-0"
                  >
                    <td className="px-4 py-2 text-zinc-500">{i + 1}</td>
                    <td className="px-4 py-2 text-zinc-200">{show.show_id}</td>
                    <td className="px-4 py-2 text-right text-zinc-300">
                      {show.plays}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* Feature Adoption */}
      {Object.keys(data.feature_adoption).length > 0 && (
        <section className="mb-8">
          <h2
            className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3 cursor-pointer hover:text-zinc-200 transition-colors"
            onClick={() => openDetail("feature_adoption")}
          >
            Feature Adoption (30d) →
          </h2>
          <div className="bg-deadly-surface rounded-lg p-4">
            {Object.entries(data.feature_adoption).map(
              ([feature, count]) => (
                <div
                  key={feature}
                  className="flex items-center gap-3 mb-2 last:mb-0"
                >
                  <span className="text-sm text-zinc-300 w-40 truncate">
                    {feature}
                  </span>
                  <div className="flex-1 bg-zinc-800 rounded-full h-4 overflow-hidden">
                    <div
                      className="bg-deadly-accent h-full rounded-full transition-all"
                      style={{
                        width: `${(count / maxFeatureUses) * 100}%`,
                      }}
                    />
                  </div>
                  <span className="text-sm text-zinc-400 w-12 text-right">
                    {count}
                  </span>
                </div>
              ),
            )}
          </div>
        </section>
      )}

      {/* Playback */}
      {data.avg_completion_rate !== null && (
        <section className="mb-8">
          <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3">
            Playback
          </h2>
          <div className="grid grid-cols-2 gap-4">
            <MetricCard
              label="Avg Completion"
              value={`${Math.round(data.avg_completion_rate * 100)}%`}
              onClick={() => openDetail("playback")}
            />
          </div>
        </section>
      )}

      {/* Detail Modal */}
      {activeMetric && (
        <div
          className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4"
          onClick={closeDetail}
        >
          <div
            className="bg-deadly-surface rounded-xl w-full max-w-4xl max-h-[80vh] flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-700">
              <h2 className="text-lg font-semibold text-white">
                {METRIC_LABELS[activeMetric]}
              </h2>
              <div className="flex items-center gap-4">
                <span className="text-xs text-zinc-500">
                  {detailRows.length} rows
                </span>
                <button
                  onClick={closeDetail}
                  className="text-zinc-400 hover:text-white text-xl leading-none"
                >
                  ×
                </button>
              </div>
            </div>

            <div className="overflow-auto flex-1">
              {detailLoading ? (
                <div className="flex items-center justify-center p-12">
                  <p className="text-zinc-400">Loading...</p>
                </div>
              ) : sortedDetail.length === 0 ? (
                <div className="flex items-center justify-center p-12">
                  <p className="text-zinc-500">No data</p>
                </div>
              ) : (
                <table className="w-full text-sm">
                  <thead className="sticky top-0 bg-deadly-surface">
                    <tr className="border-b border-zinc-700">
                      {sortedDetail.some((r) => r.detail != null) && (
                        <SortHeader
                          label="Detail"
                          sortKey="detail"
                          current={sortKey}
                          dir={sortDir}
                          onClick={toggleSort}
                        />
                      )}
                      <SortHeader
                        label="Install ID"
                        sortKey="iid"
                        current={sortKey}
                        dir={sortDir}
                        onClick={toggleSort}
                      />
                      <SortHeader
                        label="Platform"
                        sortKey="platform"
                        current={sortKey}
                        dir={sortDir}
                        onClick={toggleSort}
                      />
                      <SortHeader
                        label="Version"
                        sortKey="app_version"
                        current={sortKey}
                        dir={sortDir}
                        onClick={toggleSort}
                      />
                      <SortHeader
                        label="Last Seen"
                        sortKey="last_seen"
                        current={sortKey}
                        dir={sortDir}
                        onClick={toggleSort}
                      />
                      <SortHeader
                        label="Count"
                        sortKey="event_count"
                        current={sortKey}
                        dir={sortDir}
                        onClick={toggleSort}
                        align="right"
                      />
                    </tr>
                  </thead>
                  <tbody>
                    {sortedDetail.map((row, i) => (
                      <tr
                        key={i}
                        className="border-b border-zinc-800 last:border-0 hover:bg-zinc-800/50"
                      >
                        {sortedDetail.some((r) => r.detail != null) && (
                          <td className="px-4 py-2 text-zinc-200 max-w-[200px] truncate">
                            {row.detail ?? "—"}
                          </td>
                        )}
                        <td className="px-4 py-2 text-zinc-400 font-mono text-xs">
                          {row.iid?.slice(0, 8)}...
                        </td>
                        <td className="px-4 py-2 text-zinc-300">{row.platform}</td>
                        <td className="px-4 py-2 text-zinc-400">{row.app_version}</td>
                        <td className="px-4 py-2 text-zinc-400">{row.last_seen}</td>
                        <td className="px-4 py-2 text-right text-zinc-300">
                          {row.event_count}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function MetricCard({
  label,
  value,
  onClick,
}: {
  label: string;
  value: string | number;
  onClick?: () => void;
}) {
  return (
    <div
      className={`bg-deadly-surface rounded-lg p-4 ${
        onClick
          ? "cursor-pointer hover:bg-zinc-700/50 transition-colors"
          : ""
      }`}
      onClick={onClick}
    >
      <p className="text-xs text-zinc-400 uppercase tracking-wider mb-1">
        {label}
        {onClick && <span className="ml-1 text-zinc-600">→</span>}
      </p>
      <p className="text-3xl font-bold text-white">{value}</p>
    </div>
  );
}

function SortHeader({
  label,
  sortKey,
  current,
  dir,
  onClick,
  align,
}: {
  label: string;
  sortKey: keyof DetailRow;
  current: keyof DetailRow;
  dir: SortDir;
  onClick: (key: keyof DetailRow) => void;
  align?: "right";
}) {
  const active = current === sortKey;
  return (
    <th
      className={`px-4 py-2 text-zinc-400 cursor-pointer hover:text-zinc-200 select-none whitespace-nowrap ${
        align === "right" ? "text-right" : "text-left"
      }`}
      onClick={() => onClick(sortKey)}
    >
      {label}
      {active && (
        <span className="ml-1">{dir === "asc" ? "▲" : "▼"}</span>
      )}
    </th>
  );
}
