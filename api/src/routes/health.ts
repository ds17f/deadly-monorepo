import type { FastifyInstance } from "fastify";
import { getUsersDb } from "../db/users.js";

export async function healthRoutes(app: FastifyInstance): Promise<void> {
  app.get("/api/health", {
    schema: {
      tags: ["health"],
      summary: "Health check",
      description: "Returns the health status of the API and its dependencies",
      response: {
        200: {
          type: "object",
          description: "API is healthy",
          properties: {
            status: { type: "string", enum: ["healthy"] },
            checks: {
              type: "object",
              properties: {
                database: { type: "string", enum: ["ok", "error"] },
              },
            },
            uptime: { type: "number" },
          },
        },
        503: {
          type: "object",
          description: "API is degraded",
          properties: {
            status: { type: "string", enum: ["degraded"] },
            checks: {
              type: "object",
              properties: {
                database: { type: "string", enum: ["ok", "error"] },
              },
            },
            uptime: { type: "number" },
          },
        },
      },
    },
  }, async (_request, reply) => {
    const checks: Record<string, string> = {};

    try {
      const db = getUsersDb();
      db.prepare("SELECT 1").get();
      checks.database = "ok";
    } catch {
      checks.database = "error";
    }

    const healthy = Object.values(checks).every((v) => v === "ok");

    return reply.code(healthy ? 200 : 503).send({
      status: healthy ? "healthy" : "degraded",
      checks,
      uptime: process.uptime(),
    });
  });
}
