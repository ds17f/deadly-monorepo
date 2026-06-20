import Fastify from "fastify";
import cors from "@fastify/cors";
import formbody from "@fastify/formbody";
import websocket from "@fastify/websocket";
import swagger from "@fastify/swagger";
import swaggerUi from "@fastify/swagger-ui";
import { healthRoutes } from "./routes/health.js";
import { authRoutes } from "./auth/routes.js";
import { tokenRoutes } from "./auth/token.js";
import { mobileAuthRoutes } from "./auth/mobile.js";
import { userRoutes } from "./routes/user.js";
import { authMiddleware } from "./auth/middleware.js";
import { connectRoutes } from "./connect/routes.js";
import { connectAdminRoutes } from "./routes/connectAdmin.js";
import { connectPublicRoutes } from "./routes/connectPublic.js";
import { analyticsRoutes } from "./routes/analytics.js";
import { trendingRoutes } from "./routes/trending.js";
import { popularRoutes } from "./routes/popular.js";
import { showRoutes } from "./routes/shows.js";
import { betaRoutes } from "./routes/beta.js";
import { notificationRoutes } from "./routes/notifications.js";
import { devTokenRoutes } from "./auth/dev-token.js";
import { isDev } from "./env.js";

export function buildApp() {
  const app = Fastify({
    logger: {
      level: process.env.LOG_LEVEL ?? "info",
    },
    trustProxy: true,
  });

  const corsOrigin = process.env.CORS_ORIGIN;
  app.register(cors, {
    origin: corsOrigin && corsOrigin !== "*" ? corsOrigin.split(",") : true,
    credentials: true,
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
        { name: "analytics", description: "Anonymous usage analytics" },
        { name: "beta", description: "Beta applicant management" },
        { name: "notifications", description: "In-app messaging / admin notifications" },
      ],
    },
  });

  app.register(swaggerUi, {
    routePrefix: "/api/docs",
  });

  app.register(formbody);
  app.register(websocket);
  app.register(healthRoutes);
  app.register(authMiddleware);
  app.register(authRoutes);
  app.register(tokenRoutes);
  app.register(mobileAuthRoutes);
  app.register(userRoutes);
  app.register(connectRoutes);
  app.register(connectAdminRoutes);
  app.register(connectPublicRoutes);
  app.register(analyticsRoutes);
  app.register(trendingRoutes);
  app.register(popularRoutes);
  app.register(showRoutes);
  app.register(betaRoutes);
  app.register(notificationRoutes);

  if (isDev) {
    app.register(devTokenRoutes);
  }

  return app;
}
