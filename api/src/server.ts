import { buildApp } from "./app.js";
import { closeUsersDb } from "./db/users.js";
import { closeAnalyticsDb } from "./db/analytics.js";
import { closeRedis } from "./db/redis.js";
import { startAnalyticsSchedules } from "./routes/analytics.js";
import { startBetaSyncSchedule } from "./routes/beta.js";
import { stopHeartbeatSweep } from "./connect/state.js";

const HOST = process.env.HOST ?? "0.0.0.0";
const PORT = Number(process.env.PORT ?? 3001);

const app = buildApp();

async function start() {
  try {
    await app.listen({ host: HOST, port: PORT });
    startAnalyticsSchedules();
    startBetaSyncSchedule();
  } catch (err) {
    app.log.error(err);
    process.exit(1);
  }
}

async function shutdown() {
  app.log.info("Shutting down...");
  stopHeartbeatSweep();
  closeUsersDb();
  closeAnalyticsDb();
  await closeRedis();
  await app.close();
  process.exit(0);
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);

start();
