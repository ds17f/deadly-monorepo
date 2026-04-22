"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect, useState, useCallback, useMemo } from "react";
import MetricCard from "./components/MetricCard";
import DetailPanel from "./components/DetailPanel";
import TopShowsList from "./components/TopShowsList";
import PlatformChart from "./components/PlatformChart";
import FeatureAdoption from "./components/FeatureAdoption";
import ShowPlayback from "./components/ShowPlayback";
import CollapsibleSection from "./components/CollapsibleSection";

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

interface TimeseriesPoint {
  day: string;
  value: number;
}

interface ShowName {
  id: string;
  d: string;
  v: string;
  c: string;
  s: string;
  tc: number;
}

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

export default function AnalyticsDashboard({ showNames }: { showNames: ShowName[] }) {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const [data, setData] = useState<AnalyticsSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  // Timeseries for sparklines
  const [dauTs, setDauTs] = useState<TimeseriesPoint[]>([]);
  const [eventsTs, setEventsTs] = useState<TimeseriesPoint[]>([]);

  // Detail panel state
  const [activeMetric, setActiveMetric] = useState<DetailMetric | null>(null);
  const [activeFilter, setActiveFilter] = useState<string | undefined>(undefined);
  const [detailRows, setDetailRows] = useState<DetailRow[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);

  // Collapse all state
  const [allCollapsed, setAllCollapsed] = useState(false);
  const [collapseToggle, setCollapseToggle] = useState(0);

  const showMap = useMemo(() => {
    const m = new Map<string, ShowName>();
    for (const s of showNames) m.set(s.id, s);
    return m;
  }, [showNames]);

  const fetchSummary = useCallback(async () => {
    try {
      const res = await fetch("/api/analytics/summary", { credentials: "include" });
      if (res.status === 403) {
        setError("Forbidden");
        return;
      }
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setData(await res.json());
      setError(null);
      setLastUpdated(new Date());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load");
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchTimeseries = useCallback(async () => {
    try {
      const [dau, events] = await Promise.all([
        fetch("/api/analytics/timeseries?metric=dau&days=14", { credentials: "include" }).then((r) => r.json()),
        fetch("/api/analytics/timeseries?metric=events&days=14", { credentials: "include" }).then((r) => r.json()),
      ]);
      setDauTs(dau);
      setEventsTs(events);
    } catch {
      // non-critical
    }
  }, []);

  const fetchDetail = useCallback(async (metric: DetailMetric, filter?: string) => {
    setDetailLoading(true);
    try {
      const params = new URLSearchParams({ metric });
      if (filter) params.set("filter", filter);
      const res = await fetch(`/api/analytics/detail?${params}`, { credentials: "include" });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setDetailRows(await res.json());
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
      fetchDetail(metric, filter);
    },
    [fetchDetail],
  );

  const closeDetail = useCallback(() => {
    setActiveMetric(null);
    setActiveFilter(undefined);
    setDetailRows([]);
  }, []);

  // Auth + initial load
  useEffect(() => {
    if (authLoading) return;
    if (!user?.isAdmin) {
      router.replace("/");
      return;
    }
    fetchSummary();
    fetchTimeseries();
    const interval = setInterval(() => {
      fetchSummary();
      fetchTimeseries();
    }, REFRESH_INTERVAL);
    return () => clearInterval(interval);
  }, [authLoading, user?.isAdmin, router, fetchSummary, fetchTimeseries]);

  // Auto-refresh detail when open
  useEffect(() => {
    if (!activeMetric) return;
    const interval = setInterval(() => fetchDetail(activeMetric, activeFilter), REFRESH_INTERVAL);
    return () => clearInterval(interval);
  }, [activeMetric, activeFilter, fetchDetail]);

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

  const dauValues = dauTs.map((p) => p.value);
  const eventsValues = eventsTs.map((p) => p.value);

  const forceOpen = collapseToggle === 0 ? undefined : !allCollapsed;

  return (
    <div className="min-h-screen bg-deadly-bg p-4 sm:p-6 max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6 sm:mb-8">
        <div className="flex items-center gap-4">
          <h1 className="text-xl sm:text-2xl font-bold text-deadly-red">Analytics</h1>
          <a href="/admin/beta" className="text-sm text-zinc-500 hover:text-zinc-300">Beta &rarr;</a>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => { setAllCollapsed((c) => !c); setCollapseToggle((t) => t + 1); }}
            className="text-xs text-zinc-500 hover:text-zinc-300 transition-colors"
          >
            {allCollapsed ? "expand all" : "collapse all"}
          </button>
          <span className="text-xs text-zinc-600 hidden sm:inline">
            auto-refresh {REFRESH_INTERVAL / 1000}s
          </span>
          {lastUpdated && (
            <span className="text-xs text-zinc-500">
              {lastUpdated.toLocaleTimeString()}
            </span>
          )}
        </div>
      </div>

      {/* Active Users */}
      <CollapsibleSection title="Active Users" forceOpen={forceOpen}>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          <MetricCard label="DAU" value={data.dau} timeseries={dauValues} onClick={() => openDetail("dau")} />
          <MetricCard label="WAU" value={data.wau} timeseries={dauValues} onClick={() => openDetail("wau")} />
          <MetricCard label="MAU" value={data.mau} timeseries={dauValues} onClick={() => openDetail("mau")} />
        </div>
      </CollapsibleSection>

      {/* Install Base */}
      <CollapsibleSection title="Install Base" forceOpen={forceOpen}>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
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
            timeseries={eventsValues}
            onClick={() => openDetail("events_today")}
          />
        </div>
      </CollapsibleSection>

      {/* Platform Split */}
      <CollapsibleSection title="Platform Split (30d)" forceOpen={forceOpen} onDetail={() => openDetail("platform_split")}>
        <PlatformChart
          data={data.platform_split}
          onPlatformClick={(p) => openDetail("platform_split", p)}
        />
      </CollapsibleSection>

      {/* Top Shows */}
      <CollapsibleSection title="Top Shows (30d)" forceOpen={forceOpen} onDetail={() => openDetail("top_shows")}>
        <TopShowsList
          shows={data.top_shows}
          showMap={showMap}
          onShowClick={(id) => openDetail("top_shows", id)}
        />
      </CollapsibleSection>

      {/* Feature Adoption */}
      <CollapsibleSection title="Feature Adoption (30d)" forceOpen={forceOpen} onDetail={() => openDetail("feature_adoption")}>
        <FeatureAdoption
          data={data.feature_adoption}
          onFeatureClick={(f) => openDetail("feature_adoption", f)}
        />
      </CollapsibleSection>

      {/* Show Listening */}
      <CollapsibleSection title="Show Listening (30d)" forceOpen={forceOpen}>
        <ShowPlayback showMap={showMap} />
      </CollapsibleSection>

      {/* Detail Panel */}
      {activeMetric && (
        <DetailPanel
          title={METRIC_LABELS[activeMetric]}
          filter={activeFilter}
          rows={detailRows}
          loading={detailLoading}
          onClose={closeDetail}
          onClearFilter={activeFilter ? () => openDetail(activeMetric) : undefined}
        />
      )}
    </div>
  );
}
