import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { requireAuth } from "../auth/middleware.js";
import { getShowMeta } from "../showCatalog.js";
import {
  deleteAppUser, updateAppUserName,
  setAppUserAvatar, clearAppUserAvatar, getAppUserAvatar,
} from "../db/users.js";
import {
  getFavoriteShows, upsertFavoriteShow, deleteFavoriteShow,
  getFavoriteSongs, upsertFavoriteSong, deleteFavoriteSongByKey,
  getReviews, upsertReview, deleteReview,
  getRecordingPreferences, upsertRecordingPreference,
  getRecentShows, upsertRecentShow,
  getPlaybackPosition, upsertPlaybackPosition,
  getSettings, upsertSettings,
  getNotificationState, upsertNotificationState, upsertNotificationStates,
  getFullBackupV3, importFullBackupV3,
} from "../db/userdata.js";
import type {
  FavoriteShowV3, FavoriteTrackV3, ReviewV3,
  PlaybackPositionV3, SettingsV3, BackupV3, NotificationStateV3,
} from "../db/userdata.js";
import { getNotificationById, getAllActiveNotificationIds } from "../db/notifications.js";

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
    // Enrich with show display metadata (date/venue/city) from the catalog so
    // the web profile can render each song under its show without a second
    // lookup. Additive — mobile consumers ignore the extra fields.
    return getFavoriteSongs(request.user!.id).map((s) => {
      const meta = getShowMeta(s.showId);
      return meta ? { ...s, ...meta } : s;
    });
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
    // Enrich each review with show display metadata (venue/city/date) from
    // the in-memory catalog, same as recent/favorites, so the web profile
    // can render a proper show card without a second lookup.
    return getReviews(request.user!.id).map((r) => {
      const meta = getShowMeta(r.showId);
      return meta ? { ...r, ...meta } : r;
    });
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

  // ── Notification State (ADR-0015) ───────────────────────────────
  // Per-user read/dismiss overlay on the global message feed. Eager push:
  // markRead/archive hit the granular route, markAllRead/archiveAll hit the
  // bulk route. The feed itself stays public + cacheable; only this overlay is
  // per-user, and it rides the authed path. Union-merge (earliest non-null) is
  // handled in the db layer, so these are pure pass-throughs.

  app.get("/api/user/notifications/state", {
    schema: { tags: ["user"], summary: "List notification read/dismiss overlay" },
    preHandler: requireAuth,
  }, async (request) => {
    return getNotificationState(request.user!.id);
  });

  // Bulk overlay write — backs markAllRead / archiveAll. Omitting `ids` targets
  // every currently-active message (uncapped), so "mark all read" covers the
  // whole active set, not just the latest few.
  app.post<{ Body: { seenAt?: unknown; dismissedAt?: unknown; ids?: unknown } }>(
    "/api/user/notifications/state", {
      schema: {
        tags: ["user"],
        summary: "Bulk mark notifications seen/dismissed",
        body: {
          type: "object",
          properties: {
            seenAt: { type: "number", nullable: true },
            dismissedAt: { type: "number", nullable: true },
            ids: { type: "array", items: { type: "number" }, nullable: true },
          },
        },
      },
      preHandler: requireAuth,
    }, async (request, reply) => {
      const { seenAt, dismissedAt, ids } = request.body;
      const seen = typeof seenAt === "number" && Number.isFinite(seenAt) ? seenAt : null;
      const dismissed = typeof dismissedAt === "number" && Number.isFinite(dismissedAt) ? dismissedAt : null;
      if (seen == null && dismissed == null) {
        return reply.code(400).send({ error: "seenAt or dismissedAt is required" });
      }
      const targetIds = Array.isArray(ids)
        ? ids.filter((n): n is number => typeof n === "number" && Number.isFinite(n))
        : getAllActiveNotificationIds();
      const rows: NotificationStateV3[] = targetIds.map((notificationId) => ({
        notificationId, seenAt: seen, dismissedAt: dismissed, updatedAt: 0,
      }));
      upsertNotificationStates(request.user!.id, rows);
      return reply.code(200).send({ count: rows.length });
    });

  // Granular overlay write — backs markRead (seenAt) / archive (dismissedAt).
  app.post<{ Params: { id: string }; Body: { seenAt?: unknown; dismissedAt?: unknown } }>(
    "/api/user/notifications/:id/state", {
      schema: {
        tags: ["user"],
        summary: "Mark a notification seen/dismissed",
        params: { type: "object", required: ["id"], properties: { id: { type: "string" } } },
        body: {
          type: "object",
          properties: {
            seenAt: { type: "number", nullable: true },
            dismissedAt: { type: "number", nullable: true },
          },
        },
      },
      preHandler: requireAuth,
    }, async (request, reply) => {
      const id = Number(request.params.id);
      if (!Number.isFinite(id)) {
        return reply.code(400).send({ error: "invalid id" });
      }
      // FK requires the message row to exist (tombstoned rows still do); reject
      // a truly-unknown id with a clean 404 rather than a constraint 500.
      if (!getNotificationById(id)) {
        return reply.code(404).send({ error: "not found" });
      }
      const { seenAt, dismissedAt } = request.body;
      const seen = typeof seenAt === "number" && Number.isFinite(seenAt) ? seenAt : null;
      const dismissed = typeof dismissedAt === "number" && Number.isFinite(dismissedAt) ? dismissedAt : null;
      if (seen == null && dismissed == null) {
        return reply.code(400).send({ error: "seenAt or dismissedAt is required" });
      }
      upsertNotificationState(request.user!.id, {
        notificationId: id, seenAt: seen, dismissedAt: dismissed, updatedAt: 0,
      });
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

  // Update the account's display name. Source of truth for the shown name —
  // the JWT callback reads accounts.name into the session on each refresh.
  app.patch<{ Body: { name?: unknown } }>("/api/user/account", {
    schema: {
      tags: ["user"],
      summary: "Update the current account's display name",
      body: {
        type: "object",
        required: ["name"],
        properties: { name: { type: "string" } },
      },
    },
    preHandler: requireAuth,
  }, async (request, reply) => {
    const raw = typeof request.body?.name === "string" ? request.body.name.trim() : "";
    if (raw.length < 1 || raw.length > 60) {
      return reply.code(400).send({ error: "Name must be 1–60 characters." });
    }
    updateAppUserName(request.user!.id, raw);
    return reply.code(200).send({ name: raw });
  });

  // ── Avatar (profile picture) ────────────────────────────────────
  // The client downscales to a small square before upload, so the stored and
  // served bytes stay tiny (~10–25 KB). We accept the image as base64 JSON to
  // reuse the existing JSON body parser; the server caps the decoded size as a
  // safety net against an oversized upload. The public GET URL is versioned by
  // avatar_updated_at, so it's immutably cacheable and the session's
  // user.image (token.picture) points at it.

  const ALLOWED_AVATAR_MIME = new Set(["image/webp", "image/jpeg", "image/png"]);
  const MAX_AVATAR_BYTES = 256 * 1024; // safety net; client targets far smaller

  app.put<{ Body: { mime?: unknown; data?: unknown } }>("/api/user/avatar", {
    schema: {
      tags: ["user"],
      summary: "Set the current account's profile picture",
      body: {
        type: "object",
        required: ["mime", "data"],
        properties: {
          mime: { type: "string" },
          data: { type: "string", description: "base64-encoded image bytes" },
        },
      },
    },
    preHandler: requireAuth,
  }, async (request, reply) => {
    const mime = typeof request.body?.mime === "string" ? request.body.mime : "";
    const data = typeof request.body?.data === "string" ? request.body.data : "";
    if (!ALLOWED_AVATAR_MIME.has(mime)) {
      return reply.code(400).send({ error: "Unsupported image type." });
    }
    let bytes: Buffer;
    try {
      bytes = Buffer.from(data, "base64");
    } catch {
      return reply.code(400).send({ error: "Invalid image data." });
    }
    if (bytes.length === 0) {
      return reply.code(400).send({ error: "Empty image." });
    }
    if (bytes.length > MAX_AVATAR_BYTES) {
      return reply.code(413).send({ error: "Image too large." });
    }
    setAppUserAvatar(request.user!.id, bytes, mime);
    return reply.code(200).send({ image: `/api/user/avatar/${request.user!.id}` });
  });

  app.delete("/api/user/avatar", {
    schema: { tags: ["user"], summary: "Remove the current account's profile picture" },
    preHandler: requireAuth,
  }, async (request, reply) => {
    clearAppUserAvatar(request.user!.id);
    return reply.code(200).send({ ok: true });
  });

  // Public read — profile pictures are shown next to a name (social), so this
  // is unauthenticated by account id. Immutable cache: callers append
  // ?v=<avatar_updated_at>, so a new upload yields a fresh URL.
  app.get<{ Params: { id: string } }>("/api/user/avatar/:id", {
    schema: {
      tags: ["user"],
      summary: "Fetch an account's profile picture",
      params: {
        type: "object",
        required: ["id"],
        properties: { id: { type: "string" } },
      },
    },
  }, async (request, reply) => {
    const avatar = getAppUserAvatar(request.params.id);
    if (!avatar) {
      return reply.code(404).send({ error: "No avatar." });
    }
    return reply
      .header("Cache-Control", "public, max-age=31536000, immutable")
      .type(avatar.mime)
      .send(avatar.bytes);
  });
}
