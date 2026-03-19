import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { requireAuth } from "../auth/middleware.js";
import {
  getFavoriteShows, upsertFavoriteShow, deleteFavoriteShow,
  getFavoriteSongs, upsertFavoriteSong, deleteFavoriteSong,
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
    return getFavoriteShows(request.user!.id);
  });

  app.put<{ Params: { showId: string }; Body: Partial<FavoriteShowV3> }>(
    "/api/user/favorites/shows/:showId", {
      schema: { tags: ["user"], summary: "Upsert favorite show" },
      preHandler: requireAuth,
    }, async (request, reply) => {
      const show: FavoriteShowV3 = {
        showId: request.params.showId,
        addedAt: (request.body as FavoriteShowV3).addedAt ?? Math.floor(Date.now() / 1000),
        isPinned: (request.body as FavoriteShowV3).isPinned ?? false,
        ...(request.body as Partial<FavoriteShowV3>),
      };
      show.showId = request.params.showId;
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

  app.delete<{ Params: { id: string } }>(
    "/api/user/favorites/songs/:id", {
      schema: { tags: ["user"], summary: "Remove favorite song" },
      preHandler: requireAuth,
    }, async (request, reply) => {
      const deleted = deleteFavoriteSong(request.user!.id, Number(request.params.id));
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
    return getRecentShows(request.user!.id);
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
}
