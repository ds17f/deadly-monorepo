import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { requireAdmin } from "../auth/middleware.js";
import {
  createNotification,
  listNotificationsForAdmin,
  deleteNotification,
  getNotificationsSince,
  getActiveNotifications,
  getAllActiveNotificationIds,
  getLatestNotificationId,
  TITLE_MAX,
  BODY_MAX,
  CATEGORIES,
  PLATFORMS,
  type NotificationLevel,
  type NotificationCategory,
  type NotificationPlatform,
} from "../db/notifications.js";

const LEVELS: NotificationLevel[] = ["info", "warn"];

/** Coerce/validate an incoming platforms value into a clean array or null. */
function parsePlatformsInput(raw: unknown): NotificationPlatform[] | null {
  if (!Array.isArray(raw)) return null;
  const valid = raw.filter((p): p is NotificationPlatform =>
    (PLATFORMS as readonly string[]).includes(p),
  );
  return valid.length > 0 ? valid : null;
}

export async function notificationRoutes(app: FastifyInstance): Promise<void> {
  // ── Consume (public) ────────────────────────────────────────────────
  // Global content is identical for every caller and carries no per-user
  // state, so this is cacheable and needs no auth. `since` = the client's
  // cursor (last seen id); omit it for a fresh client's capped cold start.
  app.get<{ Querystring: { since?: string } }>(
    "/api/notifications",
    {
      schema: {
        tags: ["notifications"],
        summary: "Active in-app messages (delta by ?since cursor)",
        querystring: {
          type: "object",
          properties: { since: { type: "string" } },
        },
      },
    },
    async (request, reply) => {
      const raw = request.query.since;
      const cursor = raw != null && raw !== "" ? Number(raw) : null;
      // Short edge cache — the feed is the same for everyone.
      reply.header("Cache-Control", "public, max-age=30");

      // `activeIds` is the authoritative set of currently-active message ids
      // (global, so this stays cacheable). Clients prune any cached message NOT
      // in this set — the only way they learn a message was retired, since a
      // `?since` delta can't re-mention a row below the cursor (ADR-0015).
      const activeIds = getAllActiveNotificationIds();

      // Polling short-circuit: a delta poll that's already caught up skips the
      // message query (decision H). It still returns activeIds so retirement
      // reconciliation works even when nothing new arrived. Cold start (no
      // cursor) always lists messages.
      if (cursor != null && Number.isFinite(cursor) && cursor >= getLatestNotificationId()) {
        return { messages: [], cursor, activeIds };
      }

      const messages =
        cursor != null && Number.isFinite(cursor)
          ? getNotificationsSince(cursor)
          : getActiveNotifications();
      const latest = messages.reduce((max, m) => Math.max(max, m.id), cursor ?? 0);
      return { messages, cursor: latest, activeIds };
    },
  );

  // ── Admin (compose / manage) ────────────────────────────────────────
  app.post<{
    Body: {
      title?: unknown; body?: unknown; level?: unknown; category?: unknown;
      minVersion?: unknown; maxVersion?: unknown; platforms?: unknown; expiresAt?: unknown;
    };
  }>(
    "/api/admin/notifications",
    {
      schema: {
        tags: ["notifications"],
        summary: "Publish a global message (admin)",
        body: {
          type: "object",
          required: ["title", "body"],
          properties: {
            title: { type: "string" },
            body: { type: "string" },
            level: { type: "string", enum: LEVELS },
            category: { type: "string", enum: CATEGORIES },
            minVersion: { type: "string", nullable: true },
            maxVersion: { type: "string", nullable: true },
            platforms: { type: "array", items: { type: "string", enum: PLATFORMS }, nullable: true },
            expiresAt: { type: "number", nullable: true },
          },
        },
      },
      preHandler: requireAdmin,
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const { title, body, level, category, minVersion, maxVersion, platforms, expiresAt } =
        request.body as {
          title?: unknown; body?: unknown; level?: unknown; category?: unknown;
          minVersion?: unknown; maxVersion?: unknown; platforms?: unknown; expiresAt?: unknown;
        };

      const t = typeof title === "string" ? title.trim() : "";
      const b = typeof body === "string" ? body.trim() : "";
      if (!t || !b) {
        return reply.code(400).send({ error: "title and body are required" });
      }
      if (t.length > TITLE_MAX) {
        return reply.code(400).send({ error: `title exceeds ${TITLE_MAX} chars` });
      }
      if (b.length > BODY_MAX) {
        return reply.code(400).send({ error: `body exceeds ${BODY_MAX} chars` });
      }
      const lvl: NotificationLevel = level === "warn" ? "warn" : "info";
      const cat: NotificationCategory =
        typeof category === "string" && (CATEGORIES as string[]).includes(category)
          ? (category as NotificationCategory)
          : "general";
      const exp = typeof expiresAt === "number" && Number.isFinite(expiresAt) ? expiresAt : null;
      const minV = typeof minVersion === "string" && minVersion.trim() ? minVersion.trim() : null;
      const maxV = typeof maxVersion === "string" && maxVersion.trim() ? maxVersion.trim() : null;

      const created = createNotification({
        authorId: request.user!.id,
        title: t,
        body: b,
        level: lvl,
        category: cat,
        minVersion: minV,
        maxVersion: maxV,
        platforms: parsePlatformsInput(platforms),
        expiresAt: exp,
        scope: "global",
      });
      return reply.code(201).send(created);
    },
  );

  app.get(
    "/api/admin/notifications",
    {
      schema: { tags: ["notifications"], summary: "List published messages (admin)" },
      preHandler: requireAdmin,
    },
    async () => {
      return { notifications: listNotificationsForAdmin() };
    },
  );

  app.delete<{ Params: { id: string } }>(
    "/api/admin/notifications/:id",
    {
      schema: { tags: ["notifications"], summary: "Retire / unsend a message (admin)" },
      preHandler: requireAdmin,
    },
    async (request, reply) => {
      const id = Number(request.params.id);
      if (!Number.isFinite(id)) {
        return reply.code(400).send({ error: "invalid id" });
      }
      const ok = deleteNotification(id);
      if (!ok) return reply.code(404).send({ error: "not found" });
      return reply.code(204).send();
    },
  );
}
