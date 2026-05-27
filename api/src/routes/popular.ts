import type { FastifyInstance } from "fastify";
import { getPopularShows } from "../db/analytics.js";
import { getPublisher } from "../db/redis.js";

const CACHE_KEY = "popular:v1";
const CACHE_TTL_SECONDS = 600; // 10 minutes — matches trending
const POPULAR_LIMIT = 10;

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
  app.get(
    "/api/popular",
    {
      schema: {
        tags: ["analytics"],
        summary: "Popular shows for the home screen",
        description:
          "Shows ranked by net favorites / all-time logical listens. " +
          "Surfaces what listeners *kept*, complementing Trending's " +
          "what-just-got-played signal. Cached for 10 minutes in Redis.",
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
    async (_request, reply) => {
      try {
        const cached = await getPublisher().get(CACHE_KEY);
        if (cached) {
          reply.header("X-Popular-Cache", "hit");
          return reply.type("application/json").send(cached);
        }
      } catch {
        // Fall through to live query — better uncached than 500.
      }

      const fresh = getPopularShows(POPULAR_LIMIT);
      const payload = JSON.stringify(fresh);

      try {
        await getPublisher().set(CACHE_KEY, payload, "EX", CACHE_TTL_SECONDS);
      } catch {
        // Cache write best-effort.
      }

      reply.header("X-Popular-Cache", "miss");
      return reply.type("application/json").send(payload);
    },
  );
}
