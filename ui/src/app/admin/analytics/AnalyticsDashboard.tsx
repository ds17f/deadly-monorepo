"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, useCallback, useMemo } from "react";
import MetricCard from "./components/MetricCard";
import InstallDetailView from "./components/InstallDetailView";
import MetricDetailView from "./components/MetricDetailView";
import TopShowsList from "./components/TopShowsList";
import PlatformChart from "./components/PlatformChart";
import RetentionCohorts from "./components/RetentionCohorts";
import SearchQuality from "./components/SearchQuality";
import PlaysBySource from "./components/PlaysBySource";
import ListeningNow from "./components/ListeningNow";
import RecentListening from "./components/RecentListening";
import GrowthChart from "./components/GrowthChart";
import FeatureAdoption from "./components/FeatureAdoption";
import CollapsibleSection from "./components/CollapsibleSection";
import ShowEngagement, { TopShowsByAction } from "./components/ShowEngagement";

interface FeatureAdoptionEntry {
  feature: string;
  uses: number;
}

interface FeatureAdoption {
  action: FeatureAdoptionEntry[];
  preference: FeatureAdoptionEntry[];
  navigation: FeatureAdoptionEntry[];
  uncategorized: FeatureAdoptionEntry[];
}

interface AnalyticsSummary {
  dau: number;
  wau: number;
  mau: number;
  total_installs: number;
  stale_installs_30d: number;
  platform_split: Record<string, number>;
  top_shows: Array<{
    show_id: string;
    listeners: number;
    track_plays: number;
    completion_rate: number | null;
  }>;
  plays_by_source: Array<{
    source: string;
    plays: number;
    distinct_listeners: number;
  }>;
  top_shows_by_action: TopShowsByAction;
  feature_adoption: FeatureAdoption;
  avg_completion_rate: number | null;
  avg_completion_sample_count: number;
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
  img: string | null;
}

type DetailMetric =
  | "dau" | "wau" | "mau"
  | "total_installs" | "stale_installs"
  | "events_today"
  | "top_shows"
  | "feature_adoption"
  | "platform_split"
  | "playback"
  | "playback_source"
  | "new_installs";

const METRIC_LABELS: Record<DetailMetric, string> = {
  dau: "Daily Active Users",
  wau: "Weekly Active Users",
  mau: "Monthly Active Users",
  total_installs: "Total Installs",
  stale_installs: "Stale Installs (30d)",
  events_today: "Events Today",
  top_shows: "Most-listened shows (30d)",
  feature_adoption: "Feature Adoption (30d)",
  platform_split: "Active installs by platform (30d)",
  playback: "Playback (30d)",
  playback_source: "Plays by Source (30d)",
  new_installs: "New Installs",
};

const REFRESH_INTERVAL = 30_000;
const DASH_SCROLL_KEY = "__analytics_dashboard_scroll";

export default function AnalyticsDashboard({ showNames }: { showNames: ShowName[] }) {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [data, setData] = useState<AnalyticsSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  // Timeseries for sparklines
  const [dauTs, setDauTs] = useState<TimeseriesPoint[]>([]);
  const [eventsTs, setEventsTs] = useState<TimeseriesPoint[]>([]);

  // URL-driven detail views — ?install=<iid> or ?metric=<m>&filter=<f>
  const activeInstall = searchParams.get("install");
  const activeMetric = searchParams.get("metric") as DetailMetric | null;
  const activeFilter = searchParams.get("filter") ?? undefined;

  const openInstall = useCallback(
    (iid: string) => {
      sessionStorage.setItem(DASH_SCROLL_KEY, String(window.scrollY));
      router.push(`/admin/analytics?install=${encodeURIComponent(iid)}`);
    },
    [router],
  );

  const openDetail = useCallback(
    (metric: DetailMetric, filter?: string) => {
      sessionStorage.setItem(DASH_SCROLL_KEY, String(window.scrollY));
      const params = new URLSearchParams({ metric });
      if (filter) params.set("filter", filter);
      router.push(`/admin/analytics?${params.toString()}`);
    },
    [router],
  );

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

  // Restore dashboard scroll position when returning from a detail view.
  // Panels (ListeningNow, RecentListening, TopShowsList) fetch their own
  // data after mount, so the document height grows over time. Poll until
  // the page is tall enough to actually reach the saved Y, then scroll.
  useEffect(() => {
    if (activeInstall || activeMetric) return;
    if (!data) return;
    const saved = sessionStorage.getItem(DASH_SCROLL_KEY);
    if (saved == null) return;
    sessionStorage.removeItem(DASH_SCROLL_KEY);
    const targetY = parseInt(saved, 10);
    if (!Number.isFinite(targetY)) return;

    let attempts = 0;
    let cancelled = false;
    const tryScroll = () => {
      if (cancelled) return;
      const maxY =
        document.documentElement.scrollHeight - window.innerHeight;
      if (maxY >= targetY || attempts >= 30) {
        window.scrollTo(0, Math.min(targetY, Math.max(0, maxY)));
        return;
      }
      attempts++;
      setTimeout(tryScroll, 100);
    };
    tryScroll();
    return () => {
      cancelled = true;
    };
  }, [data, activeInstall, activeMetric]);

  // Route to detail views based on URL
  if (activeInstall) {
    return <InstallDetailView iid={activeInstall} backHref="/admin/analytics" />;
  }
  if (activeMetric) {
    return (
      <MetricDetailView
        metric={activeMetric}
        filter={activeFilter}
        backHref="/admin/analytics"
      />
    );
  }

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
    <div className="min-h-screen bg-deadly-bg p-4 sm:p-6 max-w-5xl mx-auto overflow-x-hidden">
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

      {/* Listening Now */}
      <CollapsibleSection title="Listening Now (last 45 min)" forceOpen={forceOpen}>
        <ListeningNow showMap={showMap} onOpenInstall={openInstall} />
      </CollapsibleSection>

      {/* Recent Listening (finished sessions, last 24h) */}
      <CollapsibleSection title="Recent Listening (24h)" forceOpen={forceOpen}>
        <RecentListening showMap={showMap} onOpenInstall={openInstall} />
      </CollapsibleSection>

      {/* Most-listened shows */}
      <CollapsibleSection
        title="Most-listened shows"
        forceOpen={forceOpen}
        onDetail={() => openDetail("top_shows")}
      >
        <TopShowsList
          showMap={showMap}
          onShowClick={(id) => openDetail("top_shows", id)}
        />
      </CollapsibleSection>

      {/* Growth */}
      <CollapsibleSection
        title="Growth (60d)"
        forceOpen={forceOpen}
        onDetail={() => openDetail("new_installs")}
      >
        <GrowthChart onDayClick={(day) => openDetail("new_installs", day)} />
      </CollapsibleSection>

      {/* Active Users */}
      <CollapsibleSection title="Active Users" forceOpen={forceOpen}>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          <MetricCard
            label="DAU"
            value={data.dau}
            unit="user"
            hint="Distinct installs that opened the app today"
            timeseries={dauValues}
            onClick={() => openDetail("dau")}
          />
          <MetricCard
            label="WAU"
            value={data.wau}
            unit="user"
            hint="Distinct installs that opened the app in the last 7 days"
            timeseries={dauValues}
            onClick={() => openDetail("wau")}
          />
          <MetricCard
            label="MAU"
            value={data.mau}
            unit="user"
            hint="Distinct installs that opened the app in the last 30 days"
            timeseries={dauValues}
            onClick={() => openDetail("mau")}
          />
        </div>
      </CollapsibleSection>

      {/* Install Base */}
      <CollapsibleSection title="Install Base" forceOpen={forceOpen}>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          <MetricCard
            label="Total Installs"
            value={data.total_installs}
            unit="install"
            hint="Distinct iids ever seen"
            onClick={() => openDetail("total_installs")}
          />
          <MetricCard
            label="Stale (30d)"
            value={data.stale_installs_30d}
            unit="install"
            hint="Seen ever, not seen in last 30 days"
            onClick={() => openDetail("stale_installs")}
          />
          <MetricCard
            label="Events Today"
            value={data.events_today}
            unit="event"
            timeseries={eventsValues}
            onClick={() => openDetail("events_today")}
          />
          <MetricCard
            label="Avg Completion (≥1min listens, 30d)"
            value={
              data.avg_completion_rate != null
                ? `${Math.round(data.avg_completion_rate * 100)}%`
                : "—"
            }
            hint="Average of MIN(listened_ms, duration_ms) / duration_ms across playback_end events where both fields are ≥60s"
          />
        </div>
      </CollapsibleSection>

      {/* Platform Split */}
      <CollapsibleSection title="Active installs by platform (30d)" forceOpen={forceOpen} onDetail={() => openDetail("platform_split")}>
        <PlatformChart
          data={data.platform_split}
          onPlatformClick={(p) => openDetail("platform_split", p)}
        />
      </CollapsibleSection>

      {/* Plays by Source */}
      <CollapsibleSection
        title="Plays by Source (30d)"
        forceOpen={forceOpen}
        onDetail={() => openDetail("playback_source")}
      >
        <PlaysBySource
          data={data.plays_by_source}
          onSourceClick={(s) => openDetail("playback_source", s)}
        />
      </CollapsibleSection>

      {/* Retention Cohorts */}
      <CollapsibleSection title="Retention Cohorts (12 weeks)" forceOpen={forceOpen}>
        <RetentionCohorts />
      </CollapsibleSection>

      {/* Search Quality */}
      <CollapsibleSection title="Search Quality (30d)" forceOpen={forceOpen}>
        <SearchQuality />
      </CollapsibleSection>

      {/* Show Engagement (favorites / downloads / reviews / shares) */}
      <CollapsibleSection title="Show Engagement (30d)" forceOpen={forceOpen}>
        <ShowEngagement
          data={data.top_shows_by_action}
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

    </div>
  );
}
