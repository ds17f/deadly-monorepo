import type { FastifyInstance } from "fastify";
import {
  backfillShowPlaysIfEmpty,
  getTrending,
  rollupShowPlaysDay,
} from "../db/analytics.js";
import { getPublisher } from "../db/redis.js";

const CACHE_KEY = "trending:v1";
const CACHE_TTL_SECONDS = 600; // 10 minutes
const TRENDING_LIMIT = 10;

const trendingShowSchema = {
  type: "object",
  properties: {
    show_id: { type: "string" },
    sessions: { type: "number" },
    plays: { type: "number" },
  },
} as const;

export async function trendingRoutes(app: FastifyInstance): Promise<void> {
  app.get(
    "/api/trending",
    {
      schema: {
        tags: ["analytics"],
        summary: "Trending shows for the home screen",
        description:
          "Returns four time windows (now=24h, week, month, all-time) of " +
          "the most-played shows. Cached for 10 minutes in Redis.",
        response: {
          200: {
            type: "object",
            properties: {
              generated_at: { type: "string" },
              windows: {
                type: "object",
                properties: {
                  now: { type: "array", items: trendingShowSchema },
                  week: { type: "array", items: trendingShowSchema },
                  month: { type: "array", items: trendingShowSchema },
                  all: { type: "array", items: trendingShowSchema },
                },
              },
            },
          },
        },
      },
    },
    async (_request, reply) => {
      // Try Redis first. Failure (no Redis available, network blip) falls
      // through to a direct query — better to be uncached than to 500.
      try {
        const cached = await getPublisher().get(CACHE_KEY);
        if (cached) {
          reply.header("X-Trending-Cache", "hit");
          return reply.type("application/json").send(cached);
        }
      } catch {
        // Fall through to direct query.
      }

      const fresh = getTrending(TRENDING_LIMIT);
      const payload = JSON.stringify(fresh);

      try {
        await getPublisher().set(CACHE_KEY, payload, "EX", CACHE_TTL_SECONDS);
      } catch {
        // Cache write best-effort.
      }

      reply.header("X-Trending-Cache", "miss");
      return reply.type("application/json").send(payload);
    },
  );
}

// ── Scheduled tasks (call from server.ts) ────────────────────────────

/**
 * Rolls up today + yesterday on startup, then once per hour. Yesterday is
 * re-rolled to catch any late-arriving events that landed after midnight UTC.
 * Today's row is the one that actually drives the week/month/all windows
 * being fresh on the home screen.
 */
export function startTrendingSchedules(): void {
  const runRollup = (): void => {
    try {
      const today = new Date().toISOString().slice(0, 10);
      const yesterday = new Date(Date.now() - 24 * 3600 * 1000)
        .toISOString()
        .slice(0, 10);
      rollupShowPlaysDay(yesterday);
      rollupShowPlaysDay(today);
      // Bust cache so the next request reflects the fresh rollup.
      getPublisher()
        .del(CACHE_KEY)
        .catch(() => {});
    } catch (err) {
      console.error("Trending rollup error:", err);
    }
  };

  // First-run backfill: if show_plays_daily is empty, populate it from
  // every day present in analytics_events. Self-healing — drop the table
  // and restart to re-roll history.
  try {
    const filled = backfillShowPlaysIfEmpty();
    if (filled > 0) {
      console.log(`[trending] backfilled ${filled} day(s) of show plays`);
    }
  } catch (err) {
    console.error("Trending backfill error:", err);
  }

  runRollup();
  setInterval(runRollup, 3600_000).unref();
}
