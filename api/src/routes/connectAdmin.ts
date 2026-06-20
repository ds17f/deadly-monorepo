import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { requireAdmin } from "../auth/middleware.js";
import {
  getConnectEnabled,
  setConnectEnabled,
  getConnectMinProtocol,
  setConnectMinProtocol,
} from "../db/appSettings.js";
import { reconcileLiveDevices } from "../connect/state.js";

// Admin control for the global Connect (cross-device playback) kill switch and
// the minimum-protocol gate. Both are persisted in app_settings and default to
// the most permissive-when-off state (Connect off, minProtocol 0). Changes take
// effect immediately: new ws/connect registrations are gated, and any already-
// connected devices the new settings would no longer admit are disconnected.
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
      return {
        connectEnabled: getConnectEnabled(),
        connectMinProtocol: getConnectMinProtocol(),
      };
    },
  );

  // POST /api/admin/connect/settings { connectEnabled?: boolean, connectMinProtocol?: number }
  // Both fields are optional and applied independently — the min-protocol floor
  // can be set while Connect is off, so it's already in place when it's flipped on.
  app.post(
    "/api/admin/connect/settings",
    {
      schema: {
        tags: ["connect"],
        summary: "Set global Connect settings (admin)",
        body: {
          type: "object",
          properties: {
            connectEnabled: { type: "boolean" },
            connectMinProtocol: { type: "integer", minimum: 0 },
          },
        },
      },
      preHandler: requireAdmin,
    },
    async (request: FastifyRequest, reply: FastifyReply) => {
      const body = request.body as {
        connectEnabled?: boolean;
        connectMinProtocol?: number;
      };
      if (typeof body.connectEnabled === "boolean") {
        setConnectEnabled(body.connectEnabled);
      }
      if (typeof body.connectMinProtocol === "number") {
        setConnectMinProtocol(body.connectMinProtocol);
      }

      // Apply the resulting gate to live sessions immediately so a change "takes
      // effect now" rather than on the next client poll.
      const connectEnabled = getConnectEnabled();
      const connectMinProtocol = getConnectMinProtocol();
      const closed = reconcileLiveDevices(connectEnabled, connectMinProtocol);
      request.log.info(
        { connectEnabled, connectMinProtocol, closed },
        "admin: set global Connect settings",
      );
      return { connectEnabled, connectMinProtocol };
    },
  );
}
