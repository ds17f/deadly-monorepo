import fs from "fs";
import path from "path";
import type { CollectionSummary } from "@/types/homepage";

const DATA_DIR = path.join(process.cwd(), "data");

export function getAllCollections(): CollectionSummary[] {
  const filePath = path.join(DATA_DIR, "collections.json");
  const raw = JSON.parse(fs.readFileSync(filePath, "utf-8"));
  const collections: unknown[] = raw.collections ?? [];
  return collections.map((c: any) => ({
    id: c.id,
    name: c.name,
    description: c.description ?? "",
    tags: c.tags ?? [],
    total_shows: c.total_shows ?? 0,
  }));
}
