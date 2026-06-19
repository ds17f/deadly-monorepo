import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { requireAdmin } from "../auth/middleware.js";
import { getConnectEnabled, setConnectEnabled } from "../db/appSettings.js";

// Admin control for the global Connect (cross-device playback) kill switch.
// The flag is persisted in app_settings and defaults OFF; flipping it here
// takes effect immediately for new ws/connect registrations (no restart).
export async function connectAdminRoutes(app: FastifyInstance): Promise<void> {
  // GET /api/admin/connect/settings
  app.get(
    "/api/admin/connect/settings",
    {
      schema: {
        tags: ["connect"],
        summary: "Get global Connect settings (admin)",
      },
      preHandler: requireAdmin,
    },
    async () => {
      return { connectEnabled: getConnectEnabled() };
    },
  );

  // POST /api/admin/connect/settings { connectEnabled: boolean }
  app.post(
    "/api/admin/connect/settings",
    {
      schema: {
        tags: ["connect"],
        summary: "Set global Connect settings (admin)",
        body: {
          type: "object",
          required: ["connectEnabled"],
          properties: {
            connectEnabled: { type: "boolean" },
          },
        },
      },
      preHandler: requireAdmin,
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const { connectEnabled } = request.body as { connectEnabled: boolean };
      setConnectEnabled(connectEnabled);
      request.log.info({ connectEnabled }, "admin: set global Connect enabled");
      return { connectEnabled: getConnectEnabled() };
    },
  );
}
