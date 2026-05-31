import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { requireAuth } from "../auth/middleware.js";
import { getShowMeta } from "../showCatalog.js";
import { deleteAppUser } from "../db/users.js";
import {
  getFavoriteShows, upsertFavoriteShow, deleteFavoriteShow,
  getFavoriteSongs, upsertFavoriteSong, deleteFavoriteSongByKey,
  getReviews, upsertReview, deleteReview,
  getRecordingPreferences, upsertRecordingPreference,
  getRecentShows, upsertRecentShow,
  getPlaybackPosition, upsertPlaybackPosition,
  getSettings, upsertSettings,
  getFullBackupV3, importFullBackupV3,
} from "../db/userdata.js";
import type {
  FavoriteShowV3, FavoriteTrackV3, ReviewV3,
  PlaybackPositionV3, SettingsV3, BackupV3,
} from "../db/userdata.js";

export async function userRoutes(app: FastifyInstance): Promise<void> {
  // ── Full Sync ───────────────────────────────────────────────────

  app.get("/api/user/sync", {
    schema: { tags: ["user"], summary: "Export full V3 backup" },
    preHandler: requireAuth,
  }, async (request) => {
    return getFullBackupV3(request.user!.id);
  });

  app.put("/api/user/sync", {
    schema: { tags: ["user"], summary: "Import full V3 backup" },
    preHandler: requireAuth,
  }, async (request, reply) => {
    importFullBackupV3(request.user!.id, request.body as BackupV3);
    return reply.code(204).send();
  });

  // ── Favorite Shows ──────────────────────────────────────────────

  app.get("/api/user/favorites/shows", {
    schema: { tags: ["user"], summary: "List favorite shows" },
    preHandler: requireAuth,
  }, async (request) => {
    // Enrich with show display metadata (venue/city/date) from the catalog,
    // same as recents. Additive — mobile consumers ignore the extra fields.
    return getFavoriteShows(request.user!.id).map((f) => {
      const meta = getShowMeta(f.showId);
      return meta ? { ...f, ...meta } : f;
    });
  });

  app.put<{ Params: { showId: string }; Body: Partial<FavoriteShowV3> }>(
    "/api/user/favorites/shows/:showId", {
      schema: { tags: ["user"], summary: "Upsert favorite show" },
      preHandler: requireAuth,
    }, async (request, reply) => {
      const now = Math.floor(Date.now() / 1000);
      const body = request.body as Partial<FavoriteShowV3>;
      const show: FavoriteShowV3 = {
        ...body,
        showId: request.params.showId,
        addedAt: body.addedAt ?? now,
        isPinned: body.isPinned ?? false,
        updatedAt: body.updatedAt ?? now,
      };
      upsertFavoriteShow(request.user!.id, show);
      return reply.code(200).send({ ok: true });
    });

  app.delete<{ Params: { showId: string } }>(
    "/api/user/favorites/shows/:showId", {
      schema: { tags: ["user"], summary: "Remove favorite show" },
      preHandler: requireAuth,
    }, async (request, reply) => {
      const deleted = deleteFavoriteShow(request.user!.id, request.params.showId);
      return reply.code(deleted ? 200 : 404).send({ ok: deleted });
    });

  // ── Favorite Songs ──────────────────────────────────────────────

  app.get("/api/user/favorites/songs", {
    schema: { tags: ["user"], summary: "List favorite songs" },
    preHandler: requireAuth,
  }, async (request) => {
    return getFavoriteSongs(request.user!.id);
  });

  app.put("/api/user/favorites/songs", {
    schema: { tags: ["user"], summary: "Upsert favorite song" },
    preHandler: requireAuth,
  }, async (request, reply) => {
    const id = upsertFavoriteSong(request.user!.id, request.body as FavoriteTrackV3);
    return reply.code(200).send({ id });
  });

  // Delete by natural key (showId + trackTitle). Mobile clients don't have the
  // server's autoincrement id, but they do have the stable identity tuple.
  app.delete<{ Querystring: { showId?: string; trackTitle?: string } }>(
    "/api/user/favorites/songs", {
      schema: {
        tags: ["user"],
        summary: "Remove favorite song by (showId, trackTitle)",
        querystring: {
          type: "object",
          required: ["showId", "trackTitle"],
          properties: {
            showId: { type: "string" },
            trackTitle: { type: "string" },
          },
        },
      },
      preHandler: requireAuth,
    }, async (request, reply) => {
      const { showId, trackTitle } = request.query;
      const deleted = deleteFavoriteSongByKey(request.user!.id, showId!, trackTitle!);
      return reply.code(deleted ? 200 : 404).send({ ok: deleted });
    });

  // ── Reviews ─────────────────────────────────────────────────────

  app.get("/api/user/reviews", {
    schema: { tags: ["user"], summary: "List reviews" },
    preHandler: requireAuth,
  }, async (request) => {
    return getReviews(request.user!.id);
  });

  app.put<{ Params: { showId: string } }>(
    "/api/user/reviews/:showId", {
      schema: { tags: ["user"], summary: "Upsert review" },
      preHandler: requireAuth,
    }, async (request, reply) => {
      const review: ReviewV3 = {
        ...(request.body as ReviewV3),
        showId: request.params.showId,
      };
      upsertReview(request.user!.id, review);
      return reply.code(200).send({ ok: true });
    });

  app.delete<{ Params: { showId: string } }>(
    "/api/user/reviews/:showId", {
      schema: { tags: ["user"], summary: "Remove review" },
      preHandler: requireAuth,
    }, async (request, reply) => {
      const deleted = deleteReview(request.user!.id, request.params.showId);
      return reply.code(deleted ? 200 : 404).send({ ok: deleted });
    });

  // ── Recording Preferences ──────────────────────────────────────

  app.get("/api/user/recordings", {
    schema: { tags: ["user"], summary: "List recording preferences" },
    preHandler: requireAuth,
  }, async (request) => {
    return getRecordingPreferences(request.user!.id);
  });

  app.put<{ Params: { showId: string }; Body: { recordingId: string } }>(
    "/api/user/recordings/:showId", {
      schema: { tags: ["user"], summary: "Set recording preference" },
      preHandler: requireAuth,
    }, async (request, reply) => {
      const { recordingId } = request.body as { recordingId: string };
      upsertRecordingPreference(request.user!.id, request.params.showId, recordingId);
      return reply.code(200).send({ ok: true });
    });

  // ── Recent Shows ────────────────────────────────────────────────

  app.get("/api/user/recent", {
    schema: { tags: ["user"], summary: "List recent shows" },
    preHandler: requireAuth,
  }, async (request) => {
    // Enrich each recent with show display metadata (venue/city/date) from
    // the in-memory catalog. Bare record fields are preserved; meta is
    // merged in when known (null/omitted for shows not in the index).
    return getRecentShows(request.user!.id).map((r) => {
      const meta = getShowMeta(r.showId);
      return meta ? { ...r, ...meta } : r;
    });
  });

  app.put<{ Params: { showId: string } }>(
    "/api/user/recent/:showId", {
      schema: { tags: ["user"], summary: "Upsert recent show" },
      preHandler: requireAuth,
    }, async (request, reply) => {
      upsertRecentShow(request.user!.id, request.params.showId);
      return reply.code(200).send({ ok: true });
    });

  // ── Playback Position ──────────────────────────────────────────

  app.get("/api/user/position", {
    schema: { tags: ["user"], summary: "Get playback position" },
    preHandler: requireAuth,
  }, async (request) => {
    return getPlaybackPosition(request.user!.id) ?? {};
  });

  app.put("/api/user/position", {
    schema: { tags: ["user"], summary: "Update playback position" },
    preHandler: requireAuth,
  }, async (request, reply) => {
    upsertPlaybackPosition(request.user!.id, request.body as PlaybackPositionV3);
    return reply.code(200).send({ ok: true });
  });

  // ── Settings ────────────────────────────────────────────────────

  app.get("/api/user/settings", {
    schema: { tags: ["user"], summary: "Get user settings" },
    preHandler: requireAuth,
  }, async (request) => {
    return getSettings(request.user!.id) ?? {};
  });

  app.put("/api/user/settings", {
    schema: { tags: ["user"], summary: "Update user settings" },
    preHandler: requireAuth,
  }, async (request, reply) => {
    upsertSettings(request.user!.id, request.body as SettingsV3);
    return reply.code(200).send({ ok: true });
  });

  // ── Account ─────────────────────────────────────────────────────

  // Soft-delete (tombstone) the account. The row and its user-data stay,
  // but every auth path rejects a tombstoned account, so the user is
  // effectively gone until they sign in again (which reactivates it). The
  // client signs out after calling this.
  app.delete("/api/user/account", {
    schema: { tags: ["user"], summary: "Delete (tombstone) the current account" },
    preHandler: requireAuth,
  }, async (request, reply) => {
    const deleted = deleteAppUser(request.user!.id);
    return reply.code(200).send({ ok: deleted });
  });
}
