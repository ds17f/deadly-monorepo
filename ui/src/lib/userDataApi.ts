import type {
  UserDataBackupV3,
  FavoriteShow,
  ShowReview,
  PlaybackPosition,
  UserSettings,
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
