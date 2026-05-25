import type { FastifyInstance } from "fastify";
import { getTrending, rebuildShowListensRollup } from "../db/analytics.js";
import { getPublisher } from "../db/redis.js";

const CACHE_KEY = "trending:v1";
const CACHE_TTL_SECONDS = 600; // 10 minutes
const TRENDING_LIMIT = 10;

const trendingShowSchema = {
  type: "object",
  properties: {
    show_id: { type: "string" },
    listens: { type: "number" },
    plays: { type: "number" },
    installs: { type: "number" },
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
          "the most-played shows ranked by logical listens. Cached for 10 " +
          "minutes in Redis. See ADR-0004 for the ranking algorithm.",
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
      // through to the rollup table — better to be uncached than to 500.
      try {
        const cached = await getPublisher().get(CACHE_KEY);
        if (cached) {
          reply.header("X-Trending-Cache", "hit");
          return reply.type("application/json").send(cached);
        }
      } catch {
        // Fall through to rollup read.
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
 * Rebuilds `show_listens_rollup` from raw events at startup, then once per
 * hour. The rebuild is the entire job — there's no incremental path. At
 * current scale (~7k playback_start events) the scan is milliseconds; at
 * 100× scale it's still single-digit seconds in a background tick. The
 * endpoint never queries `analytics_events`.
 */
export function startTrendingSchedules(): void {
  const runRollup = (): void => {
    try {
      rebuildShowListensRollup();
      // Bust cache so the next request reflects the fresh rollup.
      getPublisher()
        .del(CACHE_KEY)
        .catch(() => {});
    } catch (err) {
      console.error("Trending rollup error:", err);
    }
  };

  runRollup();
  setInterval(runRollup, 3600_000).unref();
}
