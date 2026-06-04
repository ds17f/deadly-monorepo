import type {
  UserDataBackupV3,
  FavoriteShow,
  FavoriteTrack,
  ShowReview,
  PlaybackPosition,
  UserSettings,
  RecentShow,
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

// Returns the user's recent shows, newest first. Bare records (showId +
// timestamps + play count) — display metadata (venue/city) is derived
// client-side for now; swap to an enriched endpoint when the API gains a
// show-metadata source.
export function fetchRecentShows(): Promise<RecentShow[]> {
  return apiFetch<RecentShow[]>("/recent");
}
