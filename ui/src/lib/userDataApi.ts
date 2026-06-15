import type {
  UserDataBackupV3,
  FavoriteShow,
  FavoriteTrack,
  ShowReview,
  PlaybackPosition,
  UserSettings,
  RecentShow,
  BacklogItem,
} from "@/types/userdata";

const API_BASE = "/api/user";

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = { ...init?.headers as Record<string, string> };
  if (init?.body) {
    headers["Content-Type"] = "application/json";
  }
  const res = await fetch(`${API_BASE}${path}`, {
    credentials: "include",
    ...init,
    headers,
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    console.error(`[userDataApi] ${init?.method ?? "GET"} ${API_BASE}${path} → ${res.status}`, body);
    throw new Error(`API ${res.status}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export function fetchUserSync(): Promise<UserDataBackupV3> {
  return apiFetch<UserDataBackupV3>("/sync");
}

export function pushUserSync(data: UserDataBackupV3): Promise<void> {
  return apiFetch("/sync", {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export function updateFavoriteShow(showId: string, data: Partial<FavoriteShow> = {}): Promise<void> {
  return apiFetch(`/favorites/shows/${showId}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export function deleteFavoriteShow(showId: string): Promise<void> {
  return apiFetch(`/favorites/shows/${showId}`, { method: "DELETE" });
}

// Returns the user's favorite shows, enriched with display metadata
// (venue/city/date) from the API show catalog.
export function fetchFavoriteShows(): Promise<FavoriteShow[]> {
  return apiFetch<FavoriteShow[]>("/favorites/shows");
}

// Returns the user's favorite songs, enriched with the show's display
// metadata (venue/city/date) from the API show catalog.
export function fetchFavoriteSongs(): Promise<FavoriteTrack[]> {
  return apiFetch<FavoriteTrack[]>("/favorites/songs");
}

// Identity is the (showId, trackTitle) tuple — matching mobile and the
// server's natural key. The PUT body carries the full record.
export function updateFavoriteSong(track: FavoriteTrack): Promise<void> {
  return apiFetch("/favorites/songs", {
    method: "PUT",
    body: JSON.stringify(track),
  });
}

export function deleteFavoriteSong(showId: string, trackTitle: string): Promise<void> {
  const qs = new URLSearchParams({ showId, trackTitle });
  return apiFetch(`/favorites/songs?${qs}`, { method: "DELETE" });
}

// Soft-deletes (tombstones) the current account. Caller should sign out
// afterward. Signing in again reactivates the account.
export function deleteAccount(): Promise<void> {
  return apiFetch("/account", { method: "DELETE" });
}

// Updates the account's display name. The session follows accounts.name (JWT
// callback), so it persists across reloads; callers also update the local
// session optimistically for an instant reflection.
export function updateDisplayName(name: string): Promise<{ name: string }> {
  return apiFetch<{ name: string }>("/account", {
    method: "PATCH",
    body: JSON.stringify({ name }),
  });
}

// Uploads a profile picture. The blob is already downscaled client-side (see
// downscaleToAvatar), so it's tiny — we base64 it into a JSON body to reuse the
// JSON parser. Returns the (unversioned) avatar URL; the session's user.image
// is the source of truth and carries the cache-busting ?v= once refreshed.
export async function uploadAvatar(blob: Blob): Promise<{ image: string }> {
  const data = await blobToBase64(blob);
  return apiFetch<{ image: string }>("/avatar", {
    method: "PUT",
    body: JSON.stringify({ mime: blob.type, data }),
  });
}

// Removes the custom profile picture (reverts to the OAuth picture).
export function deleteAvatar(): Promise<void> {
  return apiFetch("/avatar", { method: "DELETE" });
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error);
    reader.onload = () => {
      // reader.result is a data URL: "data:<mime>;base64,<data>"
      const result = reader.result as string;
      resolve(result.slice(result.indexOf(",") + 1));
    };
    reader.readAsDataURL(blob);
  });
}

// Returns the user's reviews, enriched with display metadata
// (venue/city/date) from the API show catalog.
export function fetchReviews(): Promise<ShowReview[]> {
  return apiFetch<ShowReview[]>("/reviews");
}

export function updateReview(showId: string, review: Omit<ShowReview, "showId">): Promise<void> {
  return apiFetch(`/reviews/${showId}`, {
    method: "PUT",
    body: JSON.stringify(review),
  });
}

export function deleteReview(showId: string): Promise<void> {
  return apiFetch(`/reviews/${showId}`, { method: "DELETE" });
}

export function updatePlaybackPosition(position: PlaybackPosition): Promise<void> {
  return apiFetch("/position", {
    method: "PUT",
    body: JSON.stringify(position),
  });
}

export function updateSettings(settings: UserSettings): Promise<void> {
  return apiFetch("/settings", {
    method: "PUT",
    body: JSON.stringify(settings),
  });
}

export function addRecentShow(showId: string): Promise<void> {
  return apiFetch(`/recent/${showId}`, { method: "PUT" });
}

// ── Show Queue (backlog) ──────────────────────────────────────────────
// Server-backed directly (web has no local store): per-action PUT/DELETE +
// bulk reorder, refetched on focus. GET is enriched with show display metadata.

export function fetchBacklog(): Promise<BacklogItem[]> {
  return apiFetch<BacklogItem[]>("/backlog");
}

export function updateBacklogItem(showId: string, data: Partial<BacklogItem> = {}): Promise<void> {
  return apiFetch(`/backlog/${showId}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export function deleteBacklogItem(showId: string): Promise<void> {
  return apiFetch(`/backlog/${showId}`, { method: "DELETE" });
}

export function reorderBacklog(showIds: string[]): Promise<void> {
  return apiFetch("/backlog", {
    method: "PUT",
    body: JSON.stringify({ showIds }),
  });
}

// ── Notification read/dismiss overlay (ADR-0015) ──────────────────────
// Per-user seen/dismissed state synced cross-device. Timestamps on the wire
// are unix SECONDS (the rest of the user-data API's convention); the local
// notifications store keeps milliseconds, so callers convert at this boundary.

export interface NotificationStateRow {
  notificationId: number;
  seenAt?: number | null;
  dismissedAt?: number | null;
}

export function fetchNotificationState(): Promise<NotificationStateRow[]> {
  return apiFetch<NotificationStateRow[]>("/notifications/state");
}

// Granular push — backs markRead (seenAt) / archive (dismissedAt).
export function pushNotificationState(
  id: number,
  body: { seenAt?: number; dismissedAt?: number },
): Promise<void> {
  return apiFetch(`/notifications/${id}/state`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

// Bulk push — backs markAllRead / archiveAll. Omitting `ids` targets every
// currently-active message server-side.
export function pushNotificationStateBulk(
  body: { seenAt?: number; dismissedAt?: number; ids?: number[] },
): Promise<void> {
  return apiFetch("/notifications/state", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

// Returns the user's recent shows, newest first. Bare records (showId +
// timestamps + play count) — display metadata (venue/city) is derived
// client-side for now; swap to an enriched endpoint when the API gains a
// show-metadata source.
export function fetchRecentShows(): Promise<RecentShow[]> {
  return apiFetch<RecentShow[]>("/recent");
}
