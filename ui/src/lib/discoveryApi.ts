/**
 * Client for the home discovery endpoints (Trending, Fan Favorites). These are
 * the same analytics-backed APIs the mobile home rails use; web is the first
 * web consumer. Both return bare show_ids — callers resolve them to display
 * metadata against the home-page showIndex.
 */

export interface TrendingShow {
  show_id: string;
  listens: number;
  plays: number;
  installs: number;
}

export interface TrendingResponse {
  generated_at: string;
  windows: {
    now: TrendingShow[];
    week: TrendingShow[];
    month: TrendingShow[];
    all: TrendingShow[];
  };
}

export interface PopularShow {
  show_id: string;
  favorites: number;
  listens: number;
  ratio: number;
}

export interface PopularResponse {
  generated_at: string;
  decades: {
    "60s": PopularShow[];
    "70s": PopularShow[];
    "80s": PopularShow[];
    "90s": PopularShow[];
  };
}

async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(path, { credentials: "include" });
  if (!res.ok) throw new Error(`API ${res.status} ${path}`);
  return res.json() as Promise<T>;
}

export function fetchTrending(): Promise<TrendingResponse> {
  return getJson<TrendingResponse>("/api/trending");
}

export function fetchPopular(): Promise<PopularResponse> {
  return getJson<PopularResponse>("/api/popular");
}
