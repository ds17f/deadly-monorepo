"use client";

/**
 * A small showId → cover-art cache, backed by localStorage.
 *
 * Cover art is otherwise tied to the playback path (ViewedShow.image), but
 * activeShow is frequently rebuilt from the server's art-less userState
 * (Connect claim / handoff / refresh hydration), which drops the image. Rather
 * than thread art through every one of those paths, we remember it by showId
 * whenever we know it (viewing or playing a show) and look it up by showId
 * wherever art is displayed — independent of how playback started.
 */

const KEY = "deadly_art_cache";

let cache: Record<string, string> | null = null;

function load(): Record<string, string> {
  if (cache) return cache;
  if (typeof window === "undefined") return {};
  try {
    cache = JSON.parse(localStorage.getItem(KEY) || "{}");
  } catch {
    cache = {};
  }
  return cache!;
}

export function rememberArt(showId: string | null | undefined, image: string | null | undefined): void {
  if (!showId || !image) return;
  const c = load();
  if (c[showId] === image) return;
  c[showId] = image;
  try {
    localStorage.setItem(KEY, JSON.stringify(c));
  } catch {
    // ignore storage failures (quota / private mode)
  }
}

export function lookupArt(showId: string | null | undefined): string | null {
  if (!showId) return null;
  return load()[showId] ?? null;
}
