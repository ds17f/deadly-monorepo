"use client";

import { useMemo } from "react";
import { useRouter } from "next/navigation";
import { InstallEvent } from "./InstallEventTimeline";
import { ListeningRow, type TrackPlay, type TrackOutcome } from "./listeningRow";
import FeatureAdoption from "./FeatureAdoption";

interface ShowName {
  id: string;
  d: string;
  v: string;
  c: string;
  s: string;
  tc: number;
  img: string | null;
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

interface FeatureAdoptionEntry {
  feature: string;
  uses: number;
}

interface FeatureAdoptionBuckets {
  action: FeatureAdoptionEntry[];
  preference: FeatureAdoptionEntry[];
  navigation: FeatureAdoptionEntry[];
  uncategorized: FeatureAdoptionEntry[];
}

const SHOW_ID_RE = /^\d{4}-\d{2}-\d{2}(-[\w-]+)?$/;
function isShowId(value: unknown): value is string {
  return typeof value === "string" && SHOW_ID_RE.test(value);
}

function parseProps(propsStr: string | null): Record<string, unknown> {
  if (!propsStr) return {};
  try {
    return JSON.parse(propsStr) as Record<string, unknown>;
  } catch {
    return {};
  }
}

const SOURCE_LABELS: Record<string, string> = {
  auto_advance: "auto-advance",
  restore: "restore",
  browse: "show detail",
  library_favorites: "favorites",
  deeplink: "deep link",
  search_result: "search",
};

function relativeAge(ts: number): string {
  const ms = Date.now() - ts;
  if (ms < 60_000) return `${Math.floor(ms / 1000)}s ago`;
  if (ms < 3_600_000) return `${Math.floor(ms / 60_000)}m ago`;
  if (ms < 86_400_000) return `${Math.floor(ms / 3_600_000)}h ago`;
  return `${Math.floor(ms / 86_400_000)}d ago`;
}

function reasonToOutcome(reason: string | null): TrackOutcome {
  switch (reason) {
    case "completed":
      return "complete";
    case "skipped_next":
    case "skipped_prev":
      return "skipped";
    case "network_error":
      return "error";
    default:
      return "partial";
  }
}

function formatDate(d: string): string {
  const date = new Date(d + "T00:00:00");
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function Section({
  title,
  children,
  count,
}: {
  title: string;
  children: React.ReactNode;
  count?: number;
}) {
  return (
    <section className="mb-6">
      <div className="flex items-baseline justify-between mb-2">
        <h2 className="text-xs uppercase tracking-wider text-zinc-500">
          {title}
        </h2>
        {count != null && (
          <span className="text-xs text-zinc-600">{count}</span>
        )}
      </div>
      {children}
    </section>
  );
}

interface ShowAgg {
  show_id: string;
  latestTs: number;
  earliestTs: number;
  latestTrackIndex: number | null;
  latestSource: string | null;
  trackPlays: number; // total playback_start events for this show
  completionSum: number; // sum of clamped(listened_ms / duration_ms)
  completionN: number; // n of valid playback_end with listened+duration >= 60s
  // Per-track outcome map merged across all sids for this user/show.
  trackOutcomes: Map<number, { ts: number; outcome: TrackOutcome }>;
}

export default function InstallOverview({
  data,
  showMap,
}: {
  data: InstallData;
  showMap?: Map<string, ShowName>;
}) {
  const router = useRouter();

  const agg = useMemo(() => {
    const sessions = new Set<string>();
    const showAggs = new Map<string, ShowAgg>();
    const sourceCounts = new Map<string, number>();
    const searches: Array<{
      ts: number;
      query: string | null;
      result_count: number | null;
      selected_index: number | null;
    }> = [];
    const featureMap = new Map<
      string,
      { uses: number; category: string | null }
    >();
    const errors: Array<{
      ts: number;
      source: string | null;
      message: string | null;
    }> = [];

    // Helper to get-or-create a show aggregate.
    const showAgg = (show_id: string): ShowAgg => {
      let a = showAggs.get(show_id);
      if (!a) {
        a = {
          show_id,
          latestTs: 0,
          earliestTs: Infinity,
          latestTrackIndex: null,
          latestSource: null,
          trackPlays: 0,
          completionSum: 0,
          completionN: 0,
          trackOutcomes: new Map(),
        };
        showAggs.set(show_id, a);
      }
      return a;
    };

    for (const e of data.events) {
      sessions.add(e.sid);
      const p = parseProps(e.props);

      switch (e.event) {
        case "playback_start": {
          const show_id = isShowId(p.show_id) ? p.show_id : null;
          if (!show_id) break;
          const a = showAgg(show_id);
          a.trackPlays += 1;
          if (e.ts > a.latestTs) {
            a.latestTs = e.ts;
            a.latestTrackIndex =
              typeof p.track_index === "number" ? p.track_index : null;
            a.latestSource =
              typeof p.source === "string" ? p.source : null;
          }
          if (e.ts < a.earliestTs) a.earliestTs = e.ts;
          const track_index =
            typeof p.track_index === "number" ? p.track_index : 0;
          // Only set partial if no event recorded yet for this track.
          if (!a.trackOutcomes.has(track_index)) {
            a.trackOutcomes.set(track_index, { ts: e.ts, outcome: "partial" });
          }
          const src = typeof p.source === "string" ? p.source : "unknown";
          sourceCounts.set(src, (sourceCounts.get(src) ?? 0) + 1);
          break;
        }
        case "playback_end": {
          const show_id = isShowId(p.show_id) ? p.show_id : null;
          if (!show_id) break;
          const a = showAgg(show_id);
          if (e.ts > a.latestTs) {
            a.latestTs = e.ts;
          }
          const track_index =
            typeof p.track_index === "number" ? p.track_index : 0;
          const reason = typeof p.reason === "string" ? p.reason : null;
          const outcome = reasonToOutcome(reason);
          const prior = a.trackOutcomes.get(track_index);
          if (!prior || e.ts >= prior.ts) {
            a.trackOutcomes.set(track_index, { ts: e.ts, outcome });
          }
          // Completion accumulation: require both ≥60s, clamp listened ≤ duration.
          const listened =
            typeof p.listened_ms === "number" ? p.listened_ms : null;
          const duration =
            typeof p.duration_ms === "number" ? p.duration_ms : null;
          if (
            listened != null &&
            duration != null &&
            listened >= 60_000 &&
            duration >= 60_000
          ) {
            const clamped = Math.min(listened, duration);
            a.completionSum += clamped / duration;
            a.completionN += 1;
          }
          break;
        }
        case "search":
          searches.push({
            ts: e.ts,
            query: typeof p.query === "string" ? p.query : null,
            result_count:
              typeof p.result_count === "number" ? p.result_count : null,
            selected_index:
              typeof p.selected_index === "number" ? p.selected_index : null,
          });
          break;
        case "feature_use": {
          const feature = typeof p.feature === "string" ? p.feature : null;
          if (!feature) break;
          const category =
            typeof p.category === "string" ? p.category : null;
          const prior = featureMap.get(feature);
          featureMap.set(feature, {
            uses: (prior?.uses ?? 0) + 1,
            category: prior?.category ?? category,
          });
          break;
        }
        case "error":
          errors.push({
            ts: e.ts,
            source: typeof p.source === "string" ? p.source : null,
            message: typeof p.message === "string" ? p.message : null,
          });
          break;
      }
    }

    // Recent plays (deduped by show) — top 10 by latestTs DESC.
    const recentPlays = Array.from(showAggs.values())
      .sort((a, b) => b.latestTs - a.latestTs)
      .slice(0, 10);

    // Top shows — sort by trackPlays DESC, then latestTs DESC.
    const topShows = Array.from(showAggs.values())
      .sort((a, b) => {
        if (b.trackPlays !== a.trackPlays) return b.trackPlays - a.trackPlays;
        return b.latestTs - a.latestTs;
      })
      .slice(0, 10);

    const totalSourcePlays = Array.from(sourceCounts.values()).reduce(
      (a, b) => a + b,
      0,
    );
    const playsBySource = Array.from(sourceCounts.entries()).sort(
      (a, b) => b[1] - a[1],
    );

    // Feature buckets matching the server's /api/analytics/summary shape.
    const featureBuckets: FeatureAdoptionBuckets = {
      action: [],
      preference: [],
      navigation: [],
      uncategorized: [],
    };
    const sortedFeatures = Array.from(featureMap.entries()).sort(
      (a, b) => b[1].uses - a[1].uses,
    );
    for (const [feature, { uses, category }] of sortedFeatures) {
      const bucket =
        category === "action" ||
        category === "preference" ||
        category === "navigation"
          ? category
          : "uncategorized";
      featureBuckets[bucket].push({ feature, uses });
    }

    return {
      sessionCount: sessions.size,
      recentPlays,
      topShows,
      playsBySource,
      totalSourcePlays,
      searches: searches.slice(0, 10),
      featureBuckets,
      errors: errors.slice(0, 5),
    };
  }, [data.events]);

  return (
    <div>
      {/* Meta grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 mb-5">
        <Meta label="Joined" value={data.first_seen} />
        <Meta label="Last seen" value={data.last_seen} />
        <Meta label="Platform" value={data.platform} />
        <Meta label="Version" value={data.app_version} />
        <Meta label="Sessions" value={String(agg.sessionCount)} />
        <Meta label="Events" value={String(data.total_events)} />
      </div>

      {/* Recent plays — uses the same ListeningRow as the main dashboard */}
      <Section title="Recent plays" count={agg.recentPlays.length}>
        {agg.recentPlays.length === 0 ? (
          <p className="text-xs text-zinc-600 italic">No plays recorded.</p>
        ) : (
          <div className="space-y-1">
            {agg.recentPlays.map((p) => {
              const tracks: TrackPlay[] = Array.from(p.trackOutcomes.entries())
                .map(([index, v]) => ({ index, outcome: v.outcome }))
                .sort((a, b) => a.index - b.index);
              return (
                <ListeningRow
                  key={p.show_id}
                  rowKey={p.show_id}
                  iid={data.iid}
                  platform={data.platform}
                  app_version={data.app_version}
                  ts={p.latestTs}
                  show_id={p.show_id}
                  track_index={p.latestTrackIndex}
                  source={p.latestSource}
                  tracks={tracks}
                  totalTracks={showMap?.get(p.show_id)?.tc}
                />
              );
            })}
          </div>
        )}
      </Section>

      {/* Top shows — matches TopShowsList card style */}
      <Section title="Top shows played" count={agg.topShows.length}>
        {agg.topShows.length === 0 ? (
          <p className="text-xs text-zinc-600 italic">No shows played.</p>
        ) : (
          <div className="space-y-1">
            {agg.topShows.map((s, i) => {
              const info = showMap?.get(s.show_id);
              const completion =
                s.completionN > 0 ? s.completionSum / s.completionN : null;
              return (
                <div
                  key={s.show_id}
                  className="bg-deadly-surface rounded-lg p-2 flex items-center gap-3"
                >
                  <span className="text-zinc-500 text-sm font-mono w-5 text-right shrink-0 tabular-nums">
                    {i + 1}
                  </span>
                  {info?.img ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={info.img}
                      alt=""
                      loading="lazy"
                      className="w-10 h-10 object-cover rounded shrink-0 bg-zinc-800"
                    />
                  ) : (
                    <div className="w-10 h-10 rounded bg-zinc-800 shrink-0" />
                  )}
                  <a
                    href={`/shows/${s.show_id}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="min-w-0 flex-1 hover:text-deadly-blue transition-colors"
                  >
                    {info ? (
                      <>
                        <p className="text-sm text-zinc-200">
                          {formatDate(info.d)}
                        </p>
                        <p className="text-xs text-zinc-400 truncate">
                          {info.v} &mdash; {info.c}, {info.s}
                        </p>
                      </>
                    ) : (
                      <p className="text-sm text-zinc-300 font-mono break-all">
                        {s.show_id}
                      </p>
                    )}
                  </a>
                  <div className="flex flex-col items-end shrink-0 tabular-nums">
                    <span className="text-sm text-zinc-300">
                      {s.trackPlays} track{s.trackPlays !== 1 ? "s" : ""}
                    </span>
                    <span
                      className="text-xs text-zinc-500"
                      title="Average of MIN(listened, duration)/duration across this install's playback_end events"
                    >
                      {completion != null ? (
                        `${Math.round(completion * 100)}% completed`
                      ) : (
                        <span className="text-zinc-600">— completion</span>
                      )}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Section>

      {/* Plays by source */}
      <Section title="Plays by source" count={agg.playsBySource.length}>
        {agg.playsBySource.length === 0 ? (
          <p className="text-xs text-zinc-600 italic">No plays.</p>
        ) : (
          <div className="space-y-2">
            {agg.playsBySource.map(([source, count]) => {
              const pct = agg.totalSourcePlays
                ? count / agg.totalSourcePlays
                : 0;
              return (
                <div key={source}>
                  <div className="flex items-baseline justify-between text-xs mb-0.5">
                    <span className="text-zinc-300">
                      {SOURCE_LABELS[source] ?? source}
                    </span>
                    <span className="text-zinc-500 tabular-nums">
                      {count} ({Math.round(pct * 100)}%)
                    </span>
                  </div>
                  <div className="h-1.5 rounded-full bg-zinc-800 overflow-hidden">
                    <div
                      className="h-full bg-deadly-blue"
                      style={{ width: `${Math.max(2, pct * 100)}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Section>

      {/* Searches — link the query to the search-result feature filter when possible */}
      <Section title="Recent searches" count={agg.searches.length}>
        {agg.searches.length === 0 ? (
          <p className="text-xs text-zinc-600 italic">No searches.</p>
        ) : (
          <div className="space-y-1">
            {agg.searches.map((s, i) => (
              <div
                key={i}
                className="bg-zinc-800/40 rounded px-2.5 py-1.5 text-sm flex items-center gap-2 flex-wrap"
              >
                <span className="text-zinc-200 [overflow-wrap:anywhere]">
                  {s.query ? (
                    `“${s.query}”`
                  ) : (
                    <em className="text-zinc-500">empty</em>
                  )}
                </span>
                {s.result_count != null && (
                  <span className="text-xs text-zinc-500">
                    · {s.result_count} result{s.result_count === 1 ? "" : "s"}
                  </span>
                )}
                {s.selected_index != null && (
                  <span className="text-xs text-zinc-500">
                    · picked #{s.selected_index + 1}
                  </span>
                )}
                <span className="ml-auto text-xs text-zinc-600 tabular-nums">
                  {relativeAge(s.ts)}
                </span>
              </div>
            ))}
          </div>
        )}
      </Section>

      {/* Features used — same component as main dashboard */}
      <Section title="Features used">
        {agg.featureBuckets.action.length +
          agg.featureBuckets.preference.length +
          agg.featureBuckets.navigation.length +
          agg.featureBuckets.uncategorized.length ===
        0 ? (
          <p className="text-xs text-zinc-600 italic">
            No feature usage recorded.
          </p>
        ) : (
          <FeatureAdoption
            data={agg.featureBuckets}
            onFeatureClick={(f) =>
              router.push(
                `/admin/analytics?metric=feature_adoption&filter=${encodeURIComponent(f)}`,
              )
            }
          />
        )}
      </Section>

      {/* Errors (only if any) */}
      {agg.errors.length > 0 && (
        <Section title="Recent errors" count={agg.errors.length}>
          <div className="space-y-1">
            {agg.errors.map((e, i) => (
              <div
                key={i}
                className="bg-red-950/30 border-l-2 border-red-500 rounded px-2.5 py-1.5 text-xs"
              >
                {e.source && (
                  <span className="text-red-300">{e.source}</span>
                )}
                {e.message && (
                  <span className="text-zinc-400 [overflow-wrap:anywhere]">
                    {e.source ? " · " : ""}
                    {e.message}
                  </span>
                )}
                <span className="text-zinc-500 ml-2">
                  {relativeAge(e.ts)}
                </span>
              </div>
            ))}
          </div>
        </Section>
      )}
    </div>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <p className="text-xs text-zinc-500 uppercase tracking-wider">{label}</p>
      <p className="text-sm text-white break-words">{value}</p>
    </div>
  );
}
