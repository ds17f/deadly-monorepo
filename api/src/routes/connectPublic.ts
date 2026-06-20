import type { FastifyInstance } from "fastify";
import { getConnectEnabled } from "../db/appSettings.js";

// Public (unauthenticated) read of the global Connect kill switch. Clients use
// this to decide whether to render the Connect UI at all: greyed/"unavailable"
// when off, the beta opt-in / full UI when on. See ADR-0018.
//
// The flag is not secret — it only controls whether a feature is offered — so
// this needs no auth, unlike the admin GET in connectAdmin.ts which is the same
// value behind requireAdmin (and also exposes the setter).
export async function connectPublicRoutes(app: FastifyInstance): Promise<void> {
  // GET /api/connect/enabled
  app.get(
    "/api/connect/enabled",
    {
      schema: {
        tags: ["connect"],
        summary: "Get whether Connect is globally enabled (public)",
      },
    },
    async () => {
      return { connectEnabled: getConnectEnabled() };
    },
  );
}
