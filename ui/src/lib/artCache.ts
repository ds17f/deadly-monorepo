"use client";

/**
 * A small showId → {cover art, AI review} cache, backed by localStorage.
 *
 * This per-show display data is otherwise tied to the playback path
 * (ViewedShow.image / .review), but activeShow is frequently rebuilt from the
 * server's bare userState (Connect claim / handoff / refresh hydration), which
 * has neither. Rather than thread it through every one of those paths, we
 * remember it by showId whenever we know it (viewing or playing a show) and
 * look it up by showId wherever it's displayed — independent of how playback
 * started.
 */

import type { AiShowReview } from "@/types/show";

const ART_KEY = "deadly_art_cache";
const REVIEW_KEY = "deadly_review_cache";

function load<T>(key: string, ref: { v: Record<string, T> | null }): Record<string, T> {
  if (ref.v) return ref.v;
  if (typeof window === "undefined") return {};
  try {
    ref.v = JSON.parse(localStorage.getItem(key) || "{}");
  } catch {
    ref.v = {};
  }
  return ref.v!;
}

const artRef: { v: Record<string, string> | null } = { v: null };
const reviewRef: { v: Record<string, AiShowReview> | null } = { v: null };

function persist(key: string, obj: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(obj));
  } catch {
    // ignore storage failures (quota / private mode)
  }
}

export function rememberArt(showId: string | null | undefined, image: string | null | undefined): void {
  if (!showId || !image) return;
  const c = load(ART_KEY, artRef);
  if (c[showId] === image) return;
  c[showId] = image;
  persist(ART_KEY, c);
}

export function lookupArt(showId: string | null | undefined): string | null {
  if (!showId) return null;
  return load(ART_KEY, artRef)[showId] ?? null;
}

export function rememberReview(
  showId: string | null | undefined,
  review: AiShowReview | null | undefined,
): void {
  if (!showId || !review) return;
  const c = load(REVIEW_KEY, reviewRef);
  c[showId] = review;
  persist(REVIEW_KEY, c);
}

export function lookupReview(showId: string | null | undefined): AiShowReview | null {
  if (!showId) return null;
  return load(REVIEW_KEY, reviewRef)[showId] ?? null;
}
