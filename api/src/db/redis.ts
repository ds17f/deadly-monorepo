import { Redis } from "ioredis";

const REDIS_URL = process.env.REDIS_URL ?? "redis://localhost:6379";

let publisher: Redis | null = null;
let subscriber: Redis | null = null;

export function getPublisher(): Redis {
  if (!publisher) {
    publisher = new Redis(REDIS_URL, { lazyConnect: true, maxRetriesPerRequest: 3 });
    publisher.connect().catch(() => {
      // Redis is optional — Connect features degrade gracefully
    });
  }
  return publisher;
}

export function getSubscriber(): Redis {
  if (!subscriber) {
    subscriber = new Redis(REDIS_URL, { lazyConnect: true, maxRetriesPerRequest: 3 });
    subscriber.connect().catch(() => {
      // Redis is optional
    });
  }
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
