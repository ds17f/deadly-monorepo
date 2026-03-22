"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect, useState, useCallback } from "react";

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

export default function AnalyticsDashboard() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const [data, setData] = useState<AnalyticsSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

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

  useEffect(() => {
    if (authLoading) return;
    if (!user?.isAdmin) {
      router.replace("/");
      return;
    }
    fetchSummary();
    const interval = setInterval(fetchSummary, 60_000);
    return () => clearInterval(interval);
  }, [authLoading, user?.isAdmin, router, fetchSummary]);

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
        {lastUpdated && (
          <span className="text-xs text-zinc-500">
            Updated {lastUpdated.toLocaleTimeString()}
          </span>
        )}
      </div>

      {/* Active Users */}
      <section className="mb-8">
        <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3">
          Active Users
        </h2>
        <div className="grid grid-cols-3 gap-4">
          <MetricCard label="DAU" value={data.dau} />
          <MetricCard label="WAU" value={data.wau} />
          <MetricCard label="MAU" value={data.mau} />
        </div>
      </section>

      {/* Install Base */}
      <section className="mb-8">
        <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3">
          Install Base
        </h2>
        <div className="grid grid-cols-3 gap-4">
          <MetricCard label="Total Installs" value={data.total_installs} />
          <MetricCard label="Stale (30d)" value={data.stale_installs_30d} />
          <MetricCard label="Events Today" value={data.events_today} />
        </div>
      </section>

      {/* Platform Split */}
      {totalPlatform > 0 && (
        <section className="mb-8">
          <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3">
            Platform Split (30d)
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
          <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3">
            Top Shows (30d)
          </h2>
          <div className="bg-deadly-surface rounded-lg overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-zinc-700">
                  <th className="text-left text-zinc-400 px-4 py-2">#</th>
                  <th className="text-left text-zinc-400 px-4 py-2">
                    Show
                  </th>
                  <th className="text-right text-zinc-400 px-4 py-2">
                    Plays
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.top_shows.map((show, i) => (
                  <tr
                    key={show.show_id}
                    className="border-b border-zinc-800 last:border-0"
                  >
                    <td className="px-4 py-2 text-zinc-500">{i + 1}</td>
                    <td className="px-4 py-2 text-zinc-200">
                      {show.show_id}
                    </td>
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
          <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3">
            Feature Adoption (30d)
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
            />
          </div>
        </section>
      )}
    </div>
  );
}

function MetricCard({
  label,
  value,
}: {
  label: string;
  value: string | number;
}) {
  return (
    <div className="bg-deadly-surface rounded-lg p-4">
      <p className="text-xs text-zinc-400 uppercase tracking-wider mb-1">
        {label}
      </p>
      <p className="text-3xl font-bold text-white">{value}</p>
    </div>
  );
}
