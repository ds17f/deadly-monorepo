"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, useCallback, useMemo, Suspense } from "react";

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

interface InstallEvent {
  id: number;
  event: string;
  ts: number;
  sid: string;
  platform: string;
  app_version: string;
  props: string | null;
}

interface InstallData {
  iid: string;
  platform: string;
  app_version: string;
  first_seen: string;
  last_seen: string;
  total_events: number;
  events: InstallEvent[];
}

const REFRESH_INTERVAL = 30_000;

export default function AnalyticsPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
          <p className="text-zinc-400">Loading...</p>
        </div>
      }
    >
      <AnalyticsDashboard />
    </Suspense>
  );
}

function AnalyticsDashboard() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [data, setData] = useState<AnalyticsSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [activeMetric, setActiveMetric] = useState<DetailMetric | null>(null);
  const [activeFilter, setActiveFilter] = useState<string | undefined>(undefined);
  const [detailRows, setDetailRows] = useState<DetailRow[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [sortKey, setSortKey] = useState<keyof DetailRow>("last_seen");
  const [sortDir, setSortDir] = useState<SortDir>("desc");
  const [activeIid, setActiveIid] = useState<string | null>(searchParams.get("iid"));
  const [installData, setInstallData] = useState<InstallData | null>(null);
  const [installLoading, setInstallLoading] = useState(false);
  const [installError, setInstallError] = useState<string | null>(null);
  const [installEventFilter, setInstallEventFilter] = useState<string | null>(null);
  const [installSortKey, setInstallSortKey] = useState<"ts" | "event" | "sid" | "platform" | "app_version">("ts");
  const [installSortDir, setInstallSortDir] = useState<SortDir>("desc");

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

  const fetchDetail = useCallback(async (metric: DetailMetric, filter?: string) => {
    setDetailLoading(true);
    try {
      const params = new URLSearchParams({ metric });
      if (filter) params.set("filter", filter);
      const res = await fetch(`/api/analytics/detail?${params}`, {
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
    (metric: DetailMetric, filter?: string) => {
      setActiveMetric(metric);
      setActiveFilter(filter);
      setSortKey("last_seen");
      setSortDir("desc");
      fetchDetail(metric, filter);
    },
    [fetchDetail],
  );

  const closeDetail = useCallback(() => {
    setActiveMetric(null);
    setActiveFilter(undefined);
    setDetailRows([]);
  }, []);

  const fetchInstall = useCallback(async (iid: string) => {
    setInstallLoading(true);
    setInstallError(null);
    try {
      const res = await fetch(`/api/analytics/install/${encodeURIComponent(iid)}`, {
        credentials: "include",
      });
      if (res.status === 404) {
        setInstallError("Install ID not found");
        return;
      }
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      setInstallData(json);
    } catch (e) {
      setInstallError(e instanceof Error ? e.message : "Failed to load");
    } finally {
      setInstallLoading(false);
    }
  }, []);

  const openInstall = useCallback((iid: string) => {
    setActiveIid(iid);
    setActiveMetric(null);
    setInstallEventFilter(null);
    setInstallSortKey("ts");
    setInstallSortDir("desc");
    window.history.pushState(null, "", `/admin/analytics/?iid=${encodeURIComponent(iid)}`);
    fetchInstall(iid);
  }, [fetchInstall]);

  const closeInstall = useCallback(() => {
    setActiveIid(null);
    setInstallData(null);
    setInstallError(null);
    setInstallEventFilter(null);
    window.history.pushState(null, "", "/admin/analytics/");
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
    const interval = setInterval(() => fetchDetail(activeMetric, activeFilter), REFRESH_INTERVAL);
    return () => clearInterval(interval);
  }, [activeMetric, activeFilter, fetchDetail]);

  // Load install detail from URL param on mount
  useEffect(() => {
    if (activeIid && !installData && !installLoading) {
      fetchInstall(activeIid);
    }
  }, [activeIid, installData, installLoading, fetchInstall]);

  // Handle browser back/forward navigation
  useEffect(() => {
    const onPopState = () => {
      const params = new URLSearchParams(window.location.search);
      const iid = params.get("iid");
      if (iid) {
        setActiveIid(iid);
        setInstallEventFilter(null);
        fetchInstall(iid);
      } else {
        setActiveIid(null);
        setInstallData(null);
        setInstallError(null);
        setInstallEventFilter(null);
      }
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, [fetchInstall]);

  // Auto-refresh install detail
  useEffect(() => {
    if (!activeIid) return;
    const interval = setInterval(() => fetchInstall(activeIid), REFRESH_INTERVAL);
    return () => clearInterval(interval);
  }, [activeIid, fetchInstall]);

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

  const installEventTypes = useMemo(() => {
    if (!installData) return [];
    const types = new Set(installData.events.map((e) => e.event));
    return Array.from(types).sort();
  }, [installData]);

  const sortedInstallEvents = useMemo(() => {
    if (!installData) return [];
    let rows = installData.events;
    if (installEventFilter) {
      rows = rows.filter((e) => e.event === installEventFilter);
    }
    const sorted = [...rows];
    sorted.sort((a, b) => {
      const aVal = a[installSortKey];
      const bVal = b[installSortKey];
      if (typeof aVal === "number" && typeof bVal === "number") {
        return installSortDir === "asc" ? aVal - bVal : bVal - aVal;
      }
      const cmp = String(aVal ?? "").localeCompare(String(bVal ?? ""));
      return installSortDir === "asc" ? cmp : -cmp;
    });
    return sorted;
  }, [installData, installSortKey, installSortDir, installEventFilter]);

  const toggleInstallSort = (key: typeof installSortKey) => {
    if (installSortKey === key) {
      setInstallSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setInstallSortKey(key);
      setInstallSortDir("desc");
    }
  };

  const toggleSort = (key: keyof DetailRow) => {
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("desc");
    }
  };

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

  // ── Install detail view ────────────────────────────────────────────
  if (activeIid) {
    return (
      <div className="min-h-screen bg-deadly-bg p-6 max-w-6xl mx-auto">
        <div className="flex items-center gap-3 mb-6">
          <button
            onClick={closeInstall}
            className="text-sm text-zinc-400 hover:text-white transition-colors"
          >
            &larr; Analytics
          </button>
          <h1 className="text-2xl font-bold text-deadly-red">Install Detail</h1>
        </div>

        {installError && (
          <div className="flex items-center justify-center p-12">
            <p className="text-deadly-red">{installError}</p>
          </div>
        )}

        {installLoading && !installData && (
          <div className="flex items-center justify-center p-12">
            <p className="text-zinc-400">Loading events...</p>
          </div>
        )}

        {installData && (
          <>
            <div className="bg-deadly-surface rounded-lg p-4 mb-6 grid grid-cols-2 sm:grid-cols-5 gap-4">
              <div>
                <p className="text-xs text-zinc-400 uppercase tracking-wider mb-1">Install ID</p>
                <p className="text-sm font-mono text-white break-all">{installData.iid}</p>
              </div>
              <div>
                <p className="text-xs text-zinc-400 uppercase tracking-wider mb-1">Platform</p>
                <p className="text-sm text-white">{installData.platform}</p>
              </div>
              <div>
                <p className="text-xs text-zinc-400 uppercase tracking-wider mb-1">Version</p>
                <p className="text-sm text-white">{installData.app_version}</p>
              </div>
              <div>
                <p className="text-xs text-zinc-400 uppercase tracking-wider mb-1">First Seen</p>
                <p className="text-sm text-white">{installData.first_seen}</p>
              </div>
              <div>
                <p className="text-xs text-zinc-400 uppercase tracking-wider mb-1">Last Seen</p>
                <p className="text-sm text-white">{installData.last_seen}</p>
              </div>
            </div>

            <div className="flex items-center justify-between mb-4">
              <span className="text-sm text-zinc-400">
                {sortedInstallEvents.length} event{sortedInstallEvents.length !== 1 ? "s" : ""}
                {installData.total_events > 1000 && !installEventFilter && (
                  <span className="text-zinc-600 ml-1">(showing last 1000)</span>
                )}
              </span>
              <div className="flex items-center gap-2">
                {installEventFilter && (
                  <button
                    onClick={() => setInstallEventFilter(null)}
                    className="text-xs text-zinc-400 hover:text-white transition-colors"
                  >
                    clear filter
                  </button>
                )}
                <select
                  value={installEventFilter ?? ""}
                  onChange={(e) => setInstallEventFilter(e.target.value || null)}
                  className="bg-deadly-surface text-sm text-zinc-300 rounded px-2 py-1 border border-zinc-700"
                >
                  <option value="">All events</option>
                  {installEventTypes.map((t) => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="bg-deadly-surface rounded-lg overflow-hidden">
              <div className="overflow-auto max-h-[70vh]">
                <table className="w-full text-sm">
                  <thead className="sticky top-0 bg-deadly-surface">
                    <tr className="border-b border-zinc-700">
                      <InstallSortHeader label="Time" sortKey="ts" current={installSortKey} dir={installSortDir} onClick={toggleInstallSort} />
                      <InstallSortHeader label="Event" sortKey="event" current={installSortKey} dir={installSortDir} onClick={toggleInstallSort} />
                      <InstallSortHeader label="Session" sortKey="sid" current={installSortKey} dir={installSortDir} onClick={toggleInstallSort} />
                      <InstallSortHeader label="Platform" sortKey="platform" current={installSortKey} dir={installSortDir} onClick={toggleInstallSort} />
                      <InstallSortHeader label="Version" sortKey="app_version" current={installSortKey} dir={installSortDir} onClick={toggleInstallSort} />
                      <th className="px-4 py-2 text-left text-zinc-400">Props</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sortedInstallEvents.map((evt) => (
                      <tr key={evt.id} className="border-b border-zinc-800 last:border-0 hover:bg-zinc-800/50">
                        <td className="px-4 py-2 text-zinc-400 whitespace-nowrap font-mono text-xs">
                          {new Date(evt.ts).toISOString().replace("T", " ").slice(0, 19)}
                        </td>
                        <td
                          className="px-4 py-2 text-zinc-200 cursor-pointer hover:text-white"
                          onClick={() => setInstallEventFilter(evt.event)}
                        >
                          {evt.event}
                        </td>
                        <td className="px-4 py-2 text-zinc-500 font-mono text-xs">
                          {evt.sid.slice(0, 8)}...
                        </td>
                        <td className="px-4 py-2 text-zinc-300">{evt.platform}</td>
                        <td className="px-4 py-2 text-zinc-400">{evt.app_version}</td>
                        <td className="px-4 py-2 text-zinc-500 font-mono text-xs max-w-[300px] truncate">
                          {evt.props ? formatProps(evt.props) : "\u2014"}
                        </td>
                      </tr>
                    ))}
                    {sortedInstallEvents.length === 0 && (
                      <tr>
                        <td colSpan={6} className="px-4 py-8 text-center text-zinc-500">No events</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        )}
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
        <section
          className="mb-8 cursor-pointer group"
          onClick={() => openDetail("platform_split")}
        >
          <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3 group-hover:text-zinc-200 transition-colors">
            Platform Split (30d) →
          </h2>
          <div className="bg-deadly-surface rounded-lg p-4">
            {Object.entries(data.platform_split).map(([platform, count]) => (
              <div
                key={platform}
                className="flex items-center gap-3 mb-2 last:mb-0 cursor-pointer hover:bg-zinc-700/50 rounded px-2 py-1 -mx-2 transition-colors"
                onClick={(e) => {
                  e.stopPropagation();
                  openDetail("platform_split", platform);
                }}
              >
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
        <section
          className="mb-8 cursor-pointer group"
          onClick={() => openDetail("top_shows")}
        >
          <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3 group-hover:text-zinc-200 transition-colors">
            Top Shows (30d) →
          </h2>
          <div className="bg-deadly-surface rounded-lg overflow-hidden group-hover:bg-zinc-700/50 transition-colors">
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
                    className="border-b border-zinc-800 last:border-0 cursor-pointer hover:bg-zinc-700/50 transition-colors"
                    onClick={(e) => {
                      e.stopPropagation();
                      openDetail("top_shows", show.show_id);
                    }}
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
        <section
          className="mb-8 cursor-pointer group"
          onClick={() => openDetail("feature_adoption")}
        >
          <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3 group-hover:text-zinc-200 transition-colors">
            Feature Adoption (30d) →
          </h2>
          <div className="bg-deadly-surface rounded-lg p-4">
            {Object.entries(data.feature_adoption).map(
              ([feature, count]) => (
                <div
                  key={feature}
                  className="flex items-center gap-3 mb-2 last:mb-0 cursor-pointer hover:bg-zinc-700/50 rounded px-2 py-1 -mx-2 transition-colors"
                  onClick={(e) => {
                    e.stopPropagation();
                    openDetail("feature_adoption", feature);
                  }}
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
                {activeFilter && (
                  <span className="text-sm font-normal text-zinc-400 ml-2">
                    — {activeFilter}
                  </span>
                )}
              </h2>
              <div className="flex items-center gap-4">
                {activeFilter && (
                  <button
                    onClick={() => openDetail(activeMetric, undefined)}
                    className="text-xs text-zinc-400 hover:text-white transition-colors"
                  >
                    ← all
                  </button>
                )}
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
                        <td className="px-4 py-2 font-mono text-xs">
                          <button
                            className="text-deadly-blue hover:text-white transition-colors underline decoration-zinc-600 hover:decoration-white"
                            onClick={(e) => {
                              e.stopPropagation();
                              openInstall(row.iid);
                            }}
                          >
                            {row.iid?.slice(0, 8)}...
                          </button>
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

type InstallSortKey = "ts" | "event" | "sid" | "platform" | "app_version";

function InstallSortHeader({
  label,
  sortKey,
  current,
  dir,
  onClick,
}: {
  label: string;
  sortKey: InstallSortKey;
  current: InstallSortKey;
  dir: SortDir;
  onClick: (key: InstallSortKey) => void;
}) {
  const active = current === sortKey;
  return (
    <th
      className="px-4 py-2 text-zinc-400 cursor-pointer hover:text-zinc-200 select-none whitespace-nowrap text-left"
      onClick={() => onClick(sortKey)}
    >
      {label}
      {active && (
        <span className="ml-1">{dir === "asc" ? "▲" : "▼"}</span>
      )}
    </th>
  );
}

function formatProps(propsStr: string): string {
  try {
    const obj = JSON.parse(propsStr);
    return Object.entries(obj)
      .map(([k, v]) => `${k}=${v}`)
      .join(", ");
  } catch {
    return propsStr;
  }
}
