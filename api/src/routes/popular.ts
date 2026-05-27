import type { FastifyInstance } from "fastify";
import { getPopularShows } from "../db/analytics.js";
import { getPublisher } from "../db/redis.js";

// Cache only the default home-rail size; a future "View All" page can
// hit ?limit=100 etc. and skip the cache (uncached query is cheap).
const CACHE_KEY_DEFAULT = "popular:v1";
const CACHE_TTL_SECONDS = 600; // 10 minutes — matches trending
const POPULAR_LIMIT_DEFAULT = 5;
const POPULAR_LIMIT_MAX = 100;

const popularShowSchema = {
  type: "object",
  properties: {
    show_id: { type: "string" },
    favorites: { type: "number" },
    listens: { type: "number" },
    ratio: { type: "number" },
  },
} as const;

export async function popularRoutes(app: FastifyInstance): Promise<void> {
  app.get<{ Querystring: { limit?: number } }>(
    "/api/popular",
    {
      schema: {
        tags: ["analytics"],
        summary: "Popular shows for the home screen",
        description:
          "Shows ranked first by net-favorites count, then by recency of " +
          "the most recent favorite action, then by saved-vs-played ratio. " +
          "Default returns the top 5 for the home rail; pass ?limit=N " +
          "(capped at 100) for a deeper list (future \"View All\" page).",
        querystring: {
          type: "object",
          properties: { limit: { type: "number", default: POPULAR_LIMIT_DEFAULT } },
        },
        response: {
          200: {
            type: "object",
            properties: {
              generated_at: { type: "string" },
              shows: { type: "array", items: popularShowSchema },
            },
          },
        },
      },
    },
    async (request, reply) => {
      const requested = request.query?.limit ?? POPULAR_LIMIT_DEFAULT;
      const limit = Math.min(Math.max(requested, 1), POPULAR_LIMIT_MAX);
      const isDefault = limit === POPULAR_LIMIT_DEFAULT;

      if (isDefault) {
        try {
          const cached = await getPublisher().get(CACHE_KEY_DEFAULT);
          if (cached) {
            reply.header("X-Popular-Cache", "hit");
            return reply.type("application/json").send(cached);
          }
        } catch {
          // Fall through to live query — better uncached than 500.
        }
      }

      const fresh = getPopularShows(limit);
      const payload = JSON.stringify(fresh);

      if (isDefault) {
        try {
          await getPublisher().set(
            CACHE_KEY_DEFAULT,
            payload,
            "EX",
            CACHE_TTL_SECONDS,
          );
        } catch {
          // Cache write best-effort.
        }
      }

      reply.header("X-Popular-Cache", isDefault ? "miss" : "bypass");
      return reply.type("application/json").send(payload);
    },
  );
}
