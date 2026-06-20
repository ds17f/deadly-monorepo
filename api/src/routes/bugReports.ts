import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { optionalAuth, requireAdmin } from "../auth/middleware.js";
import {
  createBugReport,
  listBugReports,
  getBugReport,
  readBugReportLogs,
  deleteBugReport,
  MAX_LOG_BYTES,
  NOTE_MAX,
} from "../db/bugReports.js";

// Per-IP rate limit for uploads (defense in depth; the app also sends the
// shared X-Analytics-Key). A real user taps "Send Bug Report" a handful of
// times at most.
const RL_WINDOW = 60 * 60_000; // 1 hour
const RL_MAX = 20;
const buckets = new Map<string, { count: number; resetAt: number }>();

function isRateLimited(ip: string): boolean {
  const now = Date.now();
  const b = buckets.get(ip);
  if (!b || now > b.resetAt) {
    buckets.set(ip, { count: 1, resetAt: now + RL_WINDOW });
    return false;
  }
  b.count++;
  return b.count > RL_MAX;
}

setInterval(() => {
  const now = Date.now();
  for (const [ip, b] of buckets) if (now > b.resetAt) buckets.delete(ip);
}, 600_000).unref();

export async function bugReportRoutes(app: FastifyInstance): Promise<void> {
  const apiKey = process.env.ANALYTICS_API_KEY;

  // ── Submit (app) ────────────────────────────────────────────────────
  // Gated by the same X-Analytics-Key the apps already embed, so random
  // internet callers can't dump files. Login is optional: when present we
  // stamp the reporter's identity, otherwise the report is anonymous.
  app.post<{
    Body: {
      logs?: unknown; note?: unknown; platform?: unknown;
      appVersion?: unknown; osVersion?: unknown; device?: unknown; installId?: unknown;
    };
  }>(
    "/api/bug-reports",
    {
      bodyLimit: MAX_LOG_BYTES + 64 * 1024,
      schema: {
        tags: ["bug-reports"],
        summary: "Submit a bug report (logs + metadata) from the app",
        body: {
          type: "object",
          required: ["logs"],
          properties: {
            logs: { type: "string" },
            note: { type: "string", nullable: true },
            platform: { type: "string", nullable: true },
            appVersion: { type: "string", nullable: true },
            osVersion: { type: "string", nullable: true },
            device: { type: "string", nullable: true },
            installId: { type: "string", nullable: true },
          },
        },
      },
      preHandler: optionalAuth,
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      if (apiKey) {
        const provided = request.headers["x-analytics-key"];
        if (provided !== apiKey) {
          return reply.code(401).send({ error: "Invalid key" });
        }
      }

      if (isRateLimited(request.ip)) {
        return reply.code(429).send({ error: "Rate limit exceeded" });
      }

      const body = request.body as {
        logs?: unknown; note?: unknown; platform?: unknown;
        appVersion?: unknown; osVersion?: unknown; device?: unknown; installId?: unknown;
      };

      const logs = typeof body.logs === "string" ? body.logs : "";
      if (!logs.trim()) {
        return reply.code(400).send({ error: "logs are required" });
      }
      if (Buffer.byteLength(logs, "utf8") > MAX_LOG_BYTES) {
        return reply.code(413).send({ error: "logs too large" });
      }

      const str = (v: unknown): string | null =>
        typeof v === "string" && v.trim() ? v.trim() : null;

      const report = createBugReport({
        logs,
        note: typeof body.note === "string" ? body.note.slice(0, NOTE_MAX) : null,
        platform: str(body.platform),
        appVersion: str(body.appVersion),
        osVersion: str(body.osVersion),
        device: str(body.device),
        installId: str(body.installId),
        userId: request.user?.id ?? null,
        userEmail: request.user?.email ?? null,
        ip: request.ip,
      });

      return reply.code(201).send({ id: report.id });
    },
  );

  // ── Admin (list / download / delete) ────────────────────────────────
  app.get(
    "/api/admin/bug-reports",
    {
      schema: { tags: ["bug-reports"], summary: "List submitted bug reports (admin)" },
      preHandler: requireAdmin,
    },
    async () => {
      return { reports: listBugReports() };
    },
  );

  app.get<{ Params: { id: string } }>(
    "/api/admin/bug-reports/:id",
    {
      schema: { tags: ["bug-reports"], summary: "Download a bug report's logs (admin)" },
      preHandler: requireAdmin,
    },
    async (request, reply) => {
      const { id } = request.params;
      const meta = getBugReport(id);
      const logs = readBugReportLogs(id);
      if (!meta || logs == null) {
        return reply.code(404).send({ error: "not found" });
      }
      reply.header("Content-Type", "text/plain; charset=utf-8");
      reply.header(
        "Content-Disposition",
        `attachment; filename="bugreport-${id}.txt"`,
      );
      return reply.send(logs);
    },
  );

  app.delete<{ Params: { id: string } }>(
    "/api/admin/bug-reports/:id",
    {
      schema: { tags: ["bug-reports"], summary: "Delete a bug report (admin)" },
      preHandler: requireAdmin,
    },
    async (request, reply) => {
      const ok = deleteBugReport(request.params.id);
      if (!ok) return reply.code(404).send({ error: "not found" });
      return reply.code(204).send();
    },
  );
}
