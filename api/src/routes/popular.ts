import type { FastifyInstance } from "fastify";
import { getPopularShows } from "../db/analytics.js";
import { getPublisher } from "../db/redis.js";

// Response is stable for the duration of the rotation window (see
// POPULAR_ROTATION_HOURS in analytics.ts). The cache key includes the
// current bucket index so a new rotation gets a fresh key and old keys
// age out naturally via TTL.
const CACHE_KEY_PREFIX = "popular:v3:b";
const CACHE_TTL_SECONDS = 6 * 3600; // > one rotation window, ages out old buckets

const popularShowSchema = {
  type: "object",
  properties: {
    show_id: { type: "string" },
    favorites: { type: "number" },
    listens: { type: "number" },
    ratio: { type: "number" },
  },
} as const;

const decadeArraySchema = { type: "array", items: popularShowSchema } as const;

function envNum(name: string, fallback: number): number {
  const raw = process.env[name];
  if (!raw) return fallback;
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

export async function popularRoutes(app: FastifyInstance): Promise<void> {
  app.get(
    "/api/popular",
    {
      schema: {
        tags: ["analytics"],
        summary: "Popular shows by decade for the home screen",
        description:
          "Returns four per-decade pools (60s/70s/80s/90s) of shows people " +
          "kept (net favorites ≥ floor), uniformly sampled via a " +
          "deterministic shuffle seeded by the current rotation window. " +
          "The client picks its 4-show display set from these pools and " +
          "re-rolls locally on 'Show more'. Floor, per-decade pool size, " +
          "and rotation window are env-tunable.",
        response: {
          200: {
            type: "object",
            properties: {
              generated_at: { type: "string" },
              decades: {
                type: "object",
                properties: {
                  "60s": decadeArraySchema,
                  "70s": decadeArraySchema,
                  "80s": decadeArraySchema,
                  "90s": decadeArraySchema,
                },
              },
            },
          },
        },
      },
    },
    async (_request, reply) => {
      const rotationHours = envNum("POPULAR_ROTATION_HOURS", 4);
      const bucket = Math.floor(Date.now() / (rotationHours * 3600 * 1000));
      const cacheKey = `${CACHE_KEY_PREFIX}${bucket}`;

      try {
        const cached = await getPublisher().get(cacheKey);
        if (cached) {
          reply.header("X-Popular-Cache", "hit");
          return reply.type("application/json").send(cached);
        }
      } catch {
        // Fall through to live query — better uncached than 500.
      }

      const fresh = getPopularShows();
      const payload = JSON.stringify(fresh);

      try {
        await getPublisher().set(cacheKey, payload, "EX", CACHE_TTL_SECONDS);
      } catch {
        // Cache write best-effort.
      }

      reply.header("X-Popular-Cache", "miss");
      return reply.type("application/json").send(payload);
    },
  );
}
