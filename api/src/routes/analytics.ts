import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import {
  VALID_EVENTS,
  allowedPropsFor,
  insertEvents,
  getSummary,
  getDetail,
  getTimeseries,
  rollupDay,
  pruneOldEvents,
  type AnalyticsEvent,
  type DetailMetric,
  type TimeseriesMetric,
  getInstallEvents,
} from "../db/analytics.js";
import { requireAdmin } from "../auth/middleware.js";

const VALID_PLATFORMS = new Set(["ios", "android", "web"]);
const MAX_EVENTS_PER_BATCH = 100;
const MAX_PROP_STRING_LENGTH = 500;

// ── Rate limiting (in-memory, per IP) ────────────────────────────────

const rateLimitWindow = 60_000; // 1 minute
const rateLimitMax = 10;
const rateBuckets = new Map<string, { count: number; resetAt: number }>();

function isRateLimited(ip: string): boolean {
  const now = Date.now();
  const bucket = rateBuckets.get(ip);
  if (!bucket || now > bucket.resetAt) {
    rateBuckets.set(ip, { count: 1, resetAt: now + rateLimitWindow });
    return false;
  }
  bucket.count++;
  return bucket.count > rateLimitMax;
}

// Clean up stale buckets every 5 minutes
setInterval(() => {
  const now = Date.now();
  for (const [ip, bucket] of rateBuckets) {
    if (now > bucket.resetAt) rateBuckets.delete(ip);
  }
}, 300_000).unref();

// ── Validation ───────────────────────────────────────────────────────

interface RawEvent {
  event?: unknown;
  ts?: unknown;
  iid?: unknown;
  sid?: unknown;
  platform?: unknown;
  app_version?: unknown;
  props?: unknown;
}

function validateAndClean(raw: RawEvent): AnalyticsEvent | null {
  if (
    typeof raw.event !== "string" ||
    typeof raw.ts !== "number" ||
    typeof raw.iid !== "string" ||
    typeof raw.sid !== "string" ||
    typeof raw.platform !== "string" ||
    typeof raw.app_version !== "string"
  ) {
    return null;
  }

  if (!VALID_EVENTS.has(raw.event)) return null;
  if (!VALID_PLATFORMS.has(raw.platform)) return null;
  if (raw.app_version.length > 20) return null;
  if (raw.iid.length > 36 || raw.sid.length > 36) return null;

  // Strip props to only allowed keys for this event type
  let cleanProps: Record<string, unknown> | undefined;
  const allowed = allowedPropsFor(raw.event);
  if (raw.props && typeof raw.props === "object" && allowed) {
    cleanProps = {};
    for (const key of allowed) {
      if (key in (raw.props as Record<string, unknown>)) {
        let val = (raw.props as Record<string, unknown>)[key];
        // Truncate long strings
        if (typeof val === "string" && val.length > MAX_PROP_STRING_LENGTH) {
          val = val.slice(0, MAX_PROP_STRING_LENGTH);
        }
        cleanProps[key] = val;
      }
    }
  }

  return {
    event: raw.event,
    ts: raw.ts,
    iid: raw.iid,
    sid: raw.sid,
    platform: raw.platform,
    app_version: raw.app_version,
    props: cleanProps,
  };
}

// ── Routes ───────────────────────────────────────────────────────────

export async function analyticsRoutes(app: FastifyInstance): Promise<void> {
  const apiKey = process.env.ANALYTICS_API_KEY;
  if (!apiKey && process.env.NODE_ENV !== "development") {
    throw new Error(
      "ANALYTICS_API_KEY must be set. Generate one with: node -e \"console.log(crypto.randomUUID())\"",
    );
  }

  // POST /api/analytics — ingest events
  app.post(
    "/api/analytics",
    {
      schema: {
        tags: ["analytics"],
        summary: "Ingest anonymous analytics events",
        description:
          "Accepts a batch of anonymous analytics events. Requires X-Analytics-Key header.",
        body: {
          type: "object",
          required: ["events"],
          properties: {
            events: {
              type: "array",
              maxItems: MAX_EVENTS_PER_BATCH,
              items: {
                type: "object",
                required: [
                  "event",
                  "ts",
                  "iid",
                  "sid",
                  "platform",
                  "app_version",
                ],
                properties: {
                  event: { type: "string" },
                  ts: { type: "number" },
                  iid: { type: "string" },
                  sid: { type: "string" },
                  platform: { type: "string" },
                  app_version: { type: "string" },
                  props: { type: "object", additionalProperties: true },
                },
              },
            },
          },
        },
        response: {
          204: { type: "null", description: "Events accepted" },
          401: {
            type: "object",
            properties: { error: { type: "string" } },
          },
          429: {
            type: "object",
            properties: { error: { type: "string" } },
          },
        },
      },
    },
    async (
      request: FastifyRequest<{
        Body: { events: RawEvent[] };
      }>,
      reply: FastifyReply,
    ) => {
      // Check API key (skipped in dev when no key is set)
      if (apiKey) {
        const provided = request.headers["x-analytics-key"];
        if (provided !== apiKey) {
          return reply.code(401).send({ error: "Invalid analytics key" });
        }
      }

      // Rate limit by IP
      const ip = request.ip;
      if (isRateLimited(ip)) {
        return reply.code(429).send({ error: "Rate limit exceeded" });
      }

      const { events: rawEvents } = request.body;
      if (!Array.isArray(rawEvents) || rawEvents.length === 0) {
        return reply.code(204).send();
      }

      // Validate and clean each event
      const valid: AnalyticsEvent[] = [];
      for (const raw of rawEvents.slice(0, MAX_EVENTS_PER_BATCH)) {
        const cleaned = validateAndClean(raw);
        if (cleaned) valid.push(cleaned);
      }

      if (valid.length > 0) {
        insertEvents(valid);
      }

      return reply.code(204).send();
    },
  );

  // GET /api/analytics/summary — admin dashboard
  app.get(
    "/api/analytics/summary",
    {
      schema: {
        tags: ["analytics"],
        summary: "Analytics summary (admin)",
        description:
          "Returns key metrics: DAU/WAU/MAU, top shows, platform split, feature adoption.",
        response: {
          200: {
            type: "object",
            properties: {
              dau: { type: "number" },
              wau: { type: "number" },
              mau: { type: "number" },
              total_installs: { type: "number" },
              stale_installs_30d: { type: "number" },
              platform_split: {
                type: "object",
                additionalProperties: { type: "number" },
              },
              top_shows: {
                type: "array",
                items: {
                  type: "object",
                  properties: {
                    show_id: { type: "string" },
                    plays: { type: "number" },
                  },
                },
              },
              feature_adoption: {
                type: "object",
                additionalProperties: { type: "number" },
              },
              avg_completion_rate: { type: ["number", "null"] },
              events_today: { type: "number" },
            },
          },
        },
      },
      preHandler: requireAdmin,
    },
    async () => {
      return getSummary();
    },
  );

  // GET /api/analytics/detail?metric=<type> — drill-down data
  const VALID_METRICS = new Set([
    "dau", "wau", "mau", "total_installs", "stale_installs",
    "events_today", "top_shows", "feature_adoption", "platform_split", "playback",
  ]);

  app.get(
    "/api/analytics/detail",
    {
      schema: {
        tags: ["analytics"],
        summary: "Analytics detail drill-down (admin)",
        querystring: {
          type: "object",
          required: ["metric"],
          properties: {
            metric: { type: "string" },
            filter: { type: "string" },
          },
        },
        response: {
          200: {
            type: "array",
            items: {
              type: "object",
              properties: {
                iid: { type: "string" },
                platform: { type: "string" },
                app_version: { type: "string" },
                last_seen: { type: "string" },
                event_count: { type: "number" },
                detail: { type: "string" },
              },
            },
          },
          400: {
            type: "object",
            properties: { error: { type: "string" } },
          },
        },
      },
      preHandler: requireAdmin,
    },
    async (request, reply) => {
      const { metric, filter } = request.query as { metric: string; filter?: string };
      if (!VALID_METRICS.has(metric)) {
        return reply.code(400).send({ error: `Invalid metric: ${metric}` });
      }
      return getDetail(metric as DetailMetric, filter);
    },
  );

  // GET /api/analytics/install/:iid — all events for an install ID
  app.get(
    "/api/analytics/install/:iid",
    {
      schema: {
        tags: ["analytics"],
        summary: "Events for a specific install ID (admin)",
        params: {
          type: "object",
          required: ["iid"],
          properties: {
            iid: { type: "string" },
          },
        },
        response: {
          200: {
            type: "object",
            properties: {
              iid: { type: "string" },
              platform: { type: "string" },
              app_version: { type: "string" },
              first_seen: { type: "string" },
              last_seen: { type: "string" },
              total_events: { type: "number" },
              events: {
                type: "array",
                items: {
                  type: "object",
                  properties: {
                    id: { type: "number" },
                    event: { type: "string" },
                    ts: { type: "number" },
                    sid: { type: "string" },
                    platform: { type: "string" },
                    app_version: { type: "string" },
                    props: { type: ["string", "null"] },
                  },
                },
              },
            },
          },
          404: {
            type: "object",
            properties: { error: { type: "string" } },
          },
        },
      },
      preHandler: requireAdmin,
    },
    async (request, reply) => {
      const { iid } = request.params as { iid: string };
      const result = getInstallEvents(iid);
      if (!result) {
        return reply.code(404).send({ error: "Install ID not found" });
      }
      return result;
    },
  );

  // GET /api/analytics/timeseries — sparkline data for admin dashboard
  const VALID_TS_METRICS = new Set(["dau", "events", "playback_starts"]);

  app.get(
    "/api/analytics/timeseries",
    {
      schema: {
        tags: ["analytics"],
        summary: "Timeseries data for dashboard sparklines (admin)",
        querystring: {
          type: "object",
          required: ["metric"],
          properties: {
            metric: { type: "string" },
            days: { type: "number", default: 14 },
          },
        },
        response: {
          200: {
            type: "array",
            items: {
              type: "object",
              properties: {
                day: { type: "string" },
                value: { type: "number" },
              },
            },
          },
          400: {
            type: "object",
            properties: { error: { type: "string" } },
          },
        },
      },
      preHandler: requireAdmin,
    },
    async (request, reply) => {
      const { metric, days } = request.query as { metric: string; days?: number };
      if (!VALID_TS_METRICS.has(metric)) {
        return reply.code(400).send({ error: `Invalid metric: ${metric}` });
      }
      const clampedDays = Math.min(Math.max(days ?? 14, 1), 90);
      return getTimeseries(metric as TimeseriesMetric, clampedDays);
    },
  );
}

// ── Scheduled tasks (call from server.ts) ────────────────────────────

export function startAnalyticsSchedules(): void {
  // Rollup yesterday's data every hour
  setInterval(() => {
    try {
      const yesterday = new Date(Date.now() - 24 * 3600 * 1000)
        .toISOString()
        .slice(0, 10);
      rollupDay(yesterday);
      pruneOldEvents();
    } catch (err) {
      console.error("Analytics rollup/prune error:", err);
    }
  }, 3600_000).unref();

  // Run once on startup for yesterday
  try {
    const yesterday = new Date(Date.now() - 24 * 3600 * 1000)
      .toISOString()
      .slice(0, 10);
    rollupDay(yesterday);
    pruneOldEvents();
  } catch (err) {
    console.error("Analytics initial rollup error:", err);
  }
}
