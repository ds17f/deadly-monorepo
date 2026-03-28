import type { FastifyInstance } from "fastify";
import { requireAdmin } from "../auth/middleware.js";
import {
  createArtist, updateArtist, deleteArtist, getArtist, listArtists,
  listPipelineRuns, getPipelineRun,
  createPipelineRun, completePipelineRun,
  getCatalogStats,
} from "../db/catalog.js";
import { getImporter, availableCollectors } from "../importers/registry.js";

/** In-memory progress logs for active pipeline runs. Keyed by runId. */
const pipelineLogs = new Map<number, { t: number; msg: string }[]>();

export async function adminRoutes(app: FastifyInstance): Promise<void> {
  // ── Artist Management ─────────────────────────────────────────

  app.post<{
    Body: {
      id: string;
      name: string;
      sort_name?: string;
      short_name?: string;
      ia_collection?: string;
      musicbrainz_id?: string;
      active_from?: number;
      active_to?: number;
      is_active?: boolean;
      description?: string;
      image_url?: string;
      website_url?: string;
      data_sources?: Record<string, string>;
    };
  }>("/api/admin/catalog/artists", {
    schema: { tags: ["admin"], summary: "Add a new artist" },
    preHandler: requireAdmin,
  }, async (request, reply) => {
    const body = request.body;

    const existing = getArtist(body.id);
    if (existing) return reply.code(409).send({ error: "Artist already exists" });

    createArtist({
      id: body.id,
      name: body.name,
      sort_name: body.sort_name ?? body.name,
      short_name: body.short_name ?? null,
      ia_collection: body.ia_collection ?? null,
      musicbrainz_id: body.musicbrainz_id ?? null,
      active_from: body.active_from ?? null,
      active_to: body.active_to ?? null,
      is_active: body.is_active ? 1 : 0,
      description: body.description ?? null,
      image_url: body.image_url ?? null,
      website_url: body.website_url ?? null,
      data_sources: JSON.stringify(body.data_sources ?? {}),
    });

    return reply.code(201).send(getArtist(body.id));
  });

  app.put<{
    Params: { id: string };
    Body: {
      name?: string;
      sort_name?: string;
      short_name?: string;
      ia_collection?: string;
      musicbrainz_id?: string;
      active_from?: number;
      active_to?: number;
      is_active?: boolean;
      description?: string;
      image_url?: string;
      website_url?: string;
      data_sources?: Record<string, string>;
    };
  }>("/api/admin/catalog/artists/:id", {
    schema: { tags: ["admin"], summary: "Update an artist" },
    preHandler: requireAdmin,
  }, async (request, reply) => {
    const existing = getArtist(request.params.id);
    if (!existing) return reply.code(404).send({ error: "Artist not found" });

    const body = request.body;
    const fields: Record<string, unknown> = {};
    if (body.name != null) fields.name = body.name;
    if (body.sort_name != null) fields.sort_name = body.sort_name;
    if (body.short_name !== undefined) fields.short_name = body.short_name;
    if (body.ia_collection !== undefined) fields.ia_collection = body.ia_collection;
    if (body.musicbrainz_id !== undefined) fields.musicbrainz_id = body.musicbrainz_id;
    if (body.active_from !== undefined) fields.active_from = body.active_from;
    if (body.active_to !== undefined) fields.active_to = body.active_to;
    if (body.is_active != null) fields.is_active = body.is_active ? 1 : 0;
    if (body.description !== undefined) fields.description = body.description;
    if (body.image_url !== undefined) fields.image_url = body.image_url;
    if (body.website_url !== undefined) fields.website_url = body.website_url;
    if (body.data_sources != null) fields.data_sources = JSON.stringify(body.data_sources);

    updateArtist(request.params.id, fields);
    return getArtist(request.params.id);
  });

  app.delete<{ Params: { id: string } }>("/api/admin/catalog/artists/:id", {
    schema: { tags: ["admin"], summary: "Delete an artist and all its data" },
    preHandler: requireAdmin,
  }, async (request, reply) => {
    const existing = getArtist(request.params.id);
    if (!existing) return reply.code(404).send({ error: "Artist not found" });
    deleteArtist(request.params.id);
    return reply.code(204).send();
  });

  // ── Backfill artist images from IA collection thumbnails ─────

  app.post("/api/admin/catalog/backfill-images", {
    schema: { tags: ["admin"], summary: "Set image_url from IA collection for artists missing one" },
    preHandler: requireAdmin,
  }, async () => {
    const artists = listArtists();
    const updated: string[] = [];
    for (const artist of artists) {
      if (!artist.image_url && artist.ia_collection) {
        const imageUrl = `https://archive.org/services/img/${encodeURIComponent(artist.ia_collection)}`;
        updateArtist(artist.id, { image_url: imageUrl });
        updated.push(artist.id);
      }
    }
    return { updated, count: updated.length };
  });

  // ── Internet Archive Lookup ──────────────────────────────────

  app.get<{
    Querystring: { q: string };
  }>("/api/admin/catalog/search-archive", {
    schema: { tags: ["admin"], summary: "Search IA for etree collections" },
    preHandler: requireAdmin,
  }, async (request, reply) => {
    const q = request.query.q?.trim();
    if (!q) return reply.code(400).send({ error: "Query parameter 'q' is required" });

    const iaUrl = new URL("https://archive.org/advancedsearch.php");
    iaUrl.searchParams.set("q", `mediatype:collection AND collection:etree AND title:(${q})`);
    iaUrl.searchParams.set("fl", "identifier,title,description");
    iaUrl.searchParams.set("rows", "20");
    iaUrl.searchParams.set("output", "json");

    const res = await fetch(iaUrl.toString());
    if (!res.ok) return reply.code(502).send({ error: "IA search failed" });

    const data = await res.json() as {
      response: { docs: { identifier: string; title: string; description: string }[] };
    };

    // For each result, get item count in parallel
    const results = await Promise.all(
      data.response.docs.map(async (doc) => {
        const countUrl = new URL("https://archive.org/advancedsearch.php");
        countUrl.searchParams.set("q", `collection:${doc.identifier} AND mediatype:etree`);
        countUrl.searchParams.set("rows", "0");
        countUrl.searchParams.set("output", "json");
        try {
          const countRes = await fetch(countUrl.toString());
          const countData = await countRes.json() as { response: { numFound: number } };
          return { ...doc, item_count: countData.response.numFound };
        } catch {
          return { ...doc, item_count: 0 };
        }
      }),
    );

    return results;
  });

  app.get<{
    Params: { id: string };
  }>("/api/admin/catalog/archive-collection/:id", {
    schema: { tags: ["admin"], summary: "Get IA collection details for artist onboarding" },
    preHandler: requireAdmin,
  }, async (request, reply) => {
    const id = request.params.id;

    // 3 parallel requests to IA
    const [metaRes, earliestRes, latestRes] = await Promise.all([
      fetch(`https://archive.org/metadata/${encodeURIComponent(id)}`),
      fetch(`https://archive.org/advancedsearch.php?${new URLSearchParams({
        q: `collection:${id} AND mediatype:etree`,
        fl: "date",
        "sort[]": "date asc",
        rows: "1",
        output: "json",
      })}`),
      fetch(`https://archive.org/advancedsearch.php?${new URLSearchParams({
        q: `collection:${id} AND mediatype:etree`,
        fl: "date",
        "sort[]": "date desc",
        rows: "1",
        output: "json",
      })}`),
    ]);

    if (!metaRes.ok) return reply.code(502).send({ error: "IA metadata fetch failed" });

    const meta = await metaRes.json() as {
      metadata: {
        identifier: string;
        title?: string;
        creator?: string;
        description?: string;
        subject?: string | string[];
      };
    };

    const earliest = await earliestRes.json() as {
      response: { numFound: number; docs: { date?: string }[] };
    };
    const latest = await latestRes.json() as {
      response: { docs: { date?: string }[] };
    };

    const earliestDate = earliest.response.docs[0]?.date;
    const latestDate = latest.response.docs[0]?.date;
    const activeFrom = earliestDate ? new Date(earliestDate).getUTCFullYear() : null;
    const latestYear = latestDate ? new Date(latestDate).getUTCFullYear() : null;
    // If latest recording is within the last 2 years, consider the artist still active
    const currentYear = new Date().getUTCFullYear();
    const activeTo = latestYear && (currentYear - latestYear) > 2 ? latestYear : null;

    const subjects = Array.isArray(meta.metadata.subject)
      ? meta.metadata.subject
      : meta.metadata.subject ? [meta.metadata.subject] : [];

    // Strip HTML from description
    const rawDesc = meta.metadata.description ?? "";
    const description = rawDesc.replace(/<[^>]*>/g, "").trim();

    return {
      identifier: meta.metadata.identifier ?? id,
      title: meta.metadata.title ?? id,
      creator: meta.metadata.creator ?? null,
      description,
      image_url: `https://archive.org/services/img/${encodeURIComponent(meta.metadata.identifier ?? id)}`,
      item_count: earliest.response.numFound,
      active_from: activeFrom,
      active_to: activeTo,
      is_active: activeTo === null,
      subjects,
    };
  });

  // ── MusicBrainz Lookup ──────────────────────────────────────────

  app.get<{
    Querystring: { q: string };
  }>("/api/admin/catalog/search-musicbrainz", {
    schema: { tags: ["admin"], summary: "Search MusicBrainz for artist MBID" },
    preHandler: requireAdmin,
  }, async (request, reply) => {
    const q = request.query.q?.trim();
    if (!q) return reply.code(400).send({ error: "Query parameter 'q' is required" });

    const url = `https://musicbrainz.org/ws/2/artist/?query=artist:${encodeURIComponent(q)}&fmt=json&limit=5`;
    const res = await fetch(url, {
      headers: { "User-Agent": "TheDeadlyApp/1.0 (https://thedeadly.app)" },
    });

    if (!res.ok) return reply.code(502).send({ error: "MusicBrainz search failed" });

    const data = await res.json() as {
      artists: {
        id: string;
        name: string;
        type?: string;
        score: number;
        area?: { name: string };
        "life-span"?: { begin?: string; end?: string; ended?: boolean };
        tags?: { name: string; count: number }[];
      }[];
    };

    return data.artists.map((a) => ({
      musicbrainz_id: a.id,
      name: a.name,
      type: a.type ?? null,
      score: a.score,
      area: a.area?.name ?? null,
      active_from: a["life-span"]?.begin ? parseInt(a["life-span"].begin, 10) || null : null,
      tags: (a.tags ?? []).slice(0, 5).map((t) => t.name),
    }));
  });

  // ── Pipeline ──────────────────────────────────────────────────

  app.post<{ Params: { artistId: string } }>("/api/admin/catalog/refresh/:artistId", {
    schema: { tags: ["admin"], summary: "Trigger a collection refresh for an artist" },
    preHandler: requireAdmin,
  }, async (request, reply) => {
    const artist = getArtist(request.params.artistId);
    if (!artist) return reply.code(404).send({ error: "Artist not found" });

    const dataSources = JSON.parse(artist.data_sources) as Record<string, string>;
    const importer = await getImporter(artist.id, dataSources);
    if (!importer) {
      return reply.code(400).send({ error: `No importer available for ${artist.name}` });
    }

    const runId = createPipelineRun(artist.id, importer.collectorType);

    // Run async — return 202 immediately, import runs in background
    reply.code(202).send({
      message: `Import started for ${artist.name}`,
      artist_id: artist.id,
      run_id: runId,
    });

    // Fire and forget — errors are captured in pipeline_runs
    pipelineLogs.set(runId, []);
    importer.run(artist.id, (msg) => {
      request.log.info({ artistId: artist.id, runId }, msg);
      pipelineLogs.get(runId)?.push({ t: Date.now(), msg });
    }).then((result) => {
      completePipelineRun(
        runId, "completed",
        result.showsProcessed + result.recordingsProcessed,
        result.showsCreated + result.recordingsCreated,
      );
      request.log.info({ artistId: artist.id, runId, result }, "Import completed");
    }).catch((err: Error) => {
      completePipelineRun(runId, "failed", 0, 0, err.message);
      pipelineLogs.get(runId)?.push({ t: Date.now(), msg: `ERROR: ${err.message}` });
      request.log.error({ artistId: artist.id, runId, err }, "Import failed");
    });
  });

  app.get<{
    Querystring: { artist_id?: string; limit?: string };
  }>("/api/admin/pipeline/status", {
    schema: { tags: ["admin"], summary: "Current pipeline state" },
    preHandler: requireAdmin,
  }, async () => {
    // Return latest run per artist + any currently running
    const artists = listArtists();
    const status = artists.map((a) => {
      const runs = listPipelineRuns(a.id, 1);
      return {
        artist_id: a.id,
        artist_name: a.name,
        last_run: runs[0] ?? null,
      };
    });
    return status;
  });

  app.get<{
    Querystring: { artist_id?: string; limit?: string };
  }>("/api/admin/pipeline/runs", {
    schema: { tags: ["admin"], summary: "Pipeline run history" },
    preHandler: requireAdmin,
  }, async (request) => {
    const limit = request.query.limit ? Number(request.query.limit) : 50;
    return listPipelineRuns(request.query.artist_id, limit);
  });

  app.get<{ Params: { id: string } }>("/api/admin/pipeline/runs/:id", {
    schema: { tags: ["admin"], summary: "Pipeline run detail" },
    preHandler: requireAdmin,
  }, async (request, reply) => {
    const run = getPipelineRun(Number(request.params.id));
    if (!run) return reply.code(404).send({ error: "Pipeline run not found" });
    return run;
  });

  app.get<{
    Params: { id: string };
    Querystring: { since?: string };
  }>("/api/admin/pipeline/runs/:id/logs", {
    schema: { tags: ["admin"], summary: "Progress logs for a pipeline run (in-memory, active runs only)" },
    preHandler: requireAdmin,
  }, async (request, reply) => {
    const runId = Number(request.params.id);
    const logs = pipelineLogs.get(runId);
    if (!logs) return reply.send({ logs: [], done: true });

    // Optional: only return logs after a timestamp (for incremental polling)
    const since = request.query.since ? Number(request.query.since) : 0;
    const filtered = since > 0 ? logs.filter((l) => l.t > since) : logs;

    const run = getPipelineRun(runId);
    const done = run?.status !== "running";

    // Clean up completed runs after client has seen them
    if (done && since > 0) {
      pipelineLogs.delete(runId);
    }

    return { logs: filtered, done };
  });

  // ── Stats ─────────────────────────────────────────────────────

  app.get("/api/admin/stats", {
    schema: { tags: ["admin"], summary: "Catalog database stats" },
    preHandler: requireAdmin,
  }, async () => {
    return getCatalogStats();
  });

  // ── Collectors ──────────────────────────────────────────────

  app.get("/api/admin/collectors", {
    schema: { tags: ["admin"], summary: "List available collector types" },
    preHandler: requireAdmin,
  }, async () => {
    return availableCollectors;
  });
}
