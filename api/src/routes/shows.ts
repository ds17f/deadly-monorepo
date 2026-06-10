import type { FastifyInstance } from "fastify";
import { getNextShow } from "../showCatalog.js";

// Minimal show-catalog read endpoints. The web client has no catalog of its own
// (shows are static SSG; the browser only has a search index without recording
// ids), so it asks the API — which holds the catalog in memory — for the next
// show to play. Backs web auto-advance (ADR-0010 chunk 2).
export async function showRoutes(app: FastifyInstance): Promise<void> {
  app.get<{ Params: { id: string } }>("/api/shows/:id/next", {
    schema: {
      tags: ["shows"],
      summary: "Next chronological show",
      description: "Returns the show immediately after the given show id by date, for auto-advance.",
      params: {
        type: "object",
        properties: { id: { type: "string" } },
        required: ["id"],
      },
    },
  }, async (request, reply) => {
    const next = getNextShow(request.params.id);
    if (!next) return reply.code(404).send({ error: "no next show" });
    return reply.send({ showId: next.showId, ...next.meta });
  });
}
