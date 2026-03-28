import type { FastifyInstance } from "fastify";
import {
  listArtists, getArtist,
  listShows, getShow, getShowsByDayOfYear,
  getRecordingsForShow, getRecording,
  getCollectionsForArtist, getShowIdsForCollection,
  searchShows,
} from "../db/catalog.js";

export async function catalogRoutes(app: FastifyInstance): Promise<void> {
  // ── Artists ──────────────────────────────────────────────────

  app.get("/api/artists", {
    schema: { tags: ["catalog"], summary: "List all artists" },
  }, async () => {
    return listArtists();
  });

  app.get<{ Params: { id: string } }>("/api/artists/:id", {
    schema: { tags: ["catalog"], summary: "Get artist detail" },
  }, async (request, reply) => {
    const artist = getArtist(request.params.id);
    if (!artist) return reply.code(404).send({ error: "Artist not found" });
    return {
      ...artist,
      data_sources: JSON.parse(artist.data_sources),
    };
  });

  // ── Shows ───────────────────────────────────────────────────

  app.get<{
    Params: { artistId: string };
    Querystring: {
      year?: string;
      month?: string;
      has_recordings?: string;
      future?: string;
      sort?: string;
      cursor?: string;
      limit?: string;
    };
  }>("/api/artists/:artistId/shows", {
    schema: { tags: ["catalog"], summary: "List shows for an artist" },
  }, async (request, reply) => {
    const artist = getArtist(request.params.artistId);
    if (!artist) return reply.code(404).send({ error: "Artist not found" });

    const q = request.query;
    const result = listShows({
      artistId: request.params.artistId,
      year: q.year ? Number(q.year) : undefined,
      month: q.month ? Number(q.month) : undefined,
      hasRecordings: q.has_recordings === "true",
      future: q.future != null ? q.future === "true" : undefined,
      sort: (q.sort as "date_asc" | "date_desc" | "rating_desc") ?? undefined,
      cursor: q.cursor,
      limit: q.limit ? Number(q.limit) : undefined,
    });

    return result;
  });

  app.get<{ Params: { id: string } }>("/api/shows/:id", {
    schema: { tags: ["catalog"], summary: "Get show detail by short ID" },
  }, async (request, reply) => {
    const show = getShow(request.params.id);
    if (!show) return reply.code(404).send({ error: "Show not found" });
    return show;
  });

  // ── Recordings ──────────────────────────────────────────────

  app.get<{ Params: { showId: string } }>("/api/shows/:showId/recordings", {
    schema: { tags: ["catalog"], summary: "List recordings for a show" },
  }, async (request, reply) => {
    const show = getShow(request.params.showId);
    if (!show) return reply.code(404).send({ error: "Show not found" });
    return getRecordingsForShow(request.params.showId);
  });

  app.get<{ Params: { id: string } }>("/api/recordings/:id", {
    schema: { tags: ["catalog"], summary: "Get recording detail" },
  }, async (request, reply) => {
    const recording = getRecording(request.params.id);
    if (!recording) return reply.code(404).send({ error: "Recording not found" });
    return recording;
  });

  // ── Collections ─────────────────────────────────────────────

  app.get<{ Params: { artistId: string } }>("/api/artists/:artistId/collections", {
    schema: { tags: ["catalog"], summary: "List collections for an artist" },
  }, async (request, reply) => {
    const artist = getArtist(request.params.artistId);
    if (!artist) return reply.code(404).send({ error: "Artist not found" });
    return getCollectionsForArtist(request.params.artistId);
  });

  app.get<{ Params: { id: string } }>("/api/collections/:id/shows", {
    schema: { tags: ["catalog"], summary: "List show IDs in a collection" },
  }, async (_request) => {
    return getShowIdsForCollection(_request.params.id);
  });

  // ── Cross-artist ────────────────────────────────────────────

  app.get<{
    Querystring: { month: string; day: string };
  }>("/api/shows/on-this-day", {
    schema: { tags: ["catalog"], summary: "Shows on this day across all artists" },
  }, async (request) => {
    const month = Number(request.query.month);
    const day = Number(request.query.day);
    // day_of_year: approximate — Jan=0-30, Feb=31-58, etc.
    // We use a lookup since day_of_year was computed from the actual date
    const targetDate = new Date(2000, month - 1, day); // leap year for Feb 29 safety
    const startOfYear = new Date(2000, 0, 1);
    const dayOfYear = Math.floor((targetDate.getTime() - startOfYear.getTime()) / 86400000) + 1;
    return getShowsByDayOfYear(dayOfYear);
  });

  app.get<{
    Querystring: { q: string; artist_id?: string; limit?: string };
  }>("/api/search", {
    schema: { tags: ["catalog"], summary: "Full-text search across shows" },
  }, async (request, reply) => {
    const q = request.query.q?.trim();
    if (!q) return reply.code(400).send({ error: "Query parameter 'q' is required" });
    const limit = request.query.limit ? Number(request.query.limit) : 50;
    return searchShows(q, request.query.artist_id, limit);
  });
}
