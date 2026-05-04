import { Redis } from "ioredis";

const REDIS_URL = process.env.REDIS_URL ?? "redis://localhost:6379";

let publisher: Redis | null = null;
let subscriber: Redis | null = null;
let warned = false;

function warnOnce(): void {
  if (warned) return;
  warned = true;
  console.warn(
    "\n[Redis] Not available at %s\n" +
    "  Connect (multi-device playback sync) will not work until Redis is running.\n" +
    "  To fix:  docker run -d --name redis -p 6379:6379 redis:7-alpine\n",
    REDIS_URL
  );
}

function makeClient(): Redis {
  const client = new Redis(REDIS_URL, { lazyConnect: true, maxRetriesPerRequest: 3 });
  client.on("error", warnOnce);
  client.connect().catch(() => {});
  return client;
}

export function getPublisher(): Redis {
  if (!publisher) publisher = makeClient();
  return publisher;
}

export function getSubscriber(): Redis {
  if (!subscriber) subscriber = makeClient();
  return subscriber;
}

export async function closeRedis(): Promise<void> {
  const tasks: Promise<void>[] = [];
  if (publisher) {
    tasks.push(publisher.quit().then(() => { publisher = null; }));
  }
  if (subscriber) {
    tasks.push(subscriber.quit().then(() => { subscriber = null; }));
  }
  await Promise.allSettled(tasks);
}
