import Fastify from "fastify";
import cors from "@fastify/cors";
import swagger from "@fastify/swagger";
import swaggerUi from "@fastify/swagger-ui";
import { healthRoutes } from "./routes/health.js";

export function buildApp() {
  const app = Fastify({
    logger: {
      level: process.env.LOG_LEVEL ?? "info",
    },
  });

  app.register(cors, {
    origin: process.env.CORS_ORIGIN ?? true,
  });

  app.register(swagger, {
    openapi: {
      info: {
        title: "The Deadly API",
        description: "API for The Deadly — Grateful Dead concert streaming app",
        version: "0.1.0",
      },
      tags: [
        { name: "health", description: "Health check endpoints" },
        { name: "auth", description: "Authentication" },
        { name: "user", description: "User data sync" },
        { name: "connect", description: "Spotify Connect-style playback" },
      ],
    },
  });

  app.register(swaggerUi, {
    routePrefix: "/api/docs",
  });

  app.register(healthRoutes);

  return app;
}
