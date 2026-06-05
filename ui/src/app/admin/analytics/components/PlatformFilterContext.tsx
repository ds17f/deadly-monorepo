"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

/** Platforms the analytics pipeline emits, with display labels for the
 *  toggle chips. Keep in sync with KNOWN_PLATFORMS in api/src/db/analytics.ts. */
export const PLATFORMS = [
  { id: "ios", label: "iOS" },
  { id: "android", label: "Android" },
  { id: "web", label: "Web" },
] as const;

export type PlatformId = (typeof PLATFORMS)[number]["id"];

const ALL_IDS = PLATFORMS.map((p) => p.id) as PlatformId[];

// `web` is browser usage, not an installed app, so the dashboard opens with
// it filtered out of every install/active-user count. Admins can flip it on.
const DEFAULT: PlatformId[] = ["ios", "android"];

const STORAGE_KEY = "analytics:platform-filter";

interface PlatformFilterValue {
  /** Currently selected platforms (always ≥1). */
  platforms: PlatformId[];
  isSelected: (p: PlatformId) => boolean;
  /** Toggle one platform on/off. Refuses to clear the last selected one so
   *  there is always at least one platform in view. */
  toggle: (p: PlatformId) => void;
  /**
   * Query value to send to the API, or "" when every platform is selected
   * (the server treats an absent/full filter as "all", so we omit it to keep
   * URLs clean and the request indistinguishable from the legacy unfiltered
   * call). Use this as a fetch dependency so panels refetch on change.
   */
  param: string;
  /** Append `?platforms=…` / `&platforms=…` to a URL when a filter is active. */
  withParam: (url: string) => string;
}

const PlatformFilterContext = createContext<PlatformFilterValue | null>(null);

function loadInitial(): PlatformId[] {
  if (typeof window === "undefined") return DEFAULT;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT;
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return DEFAULT;
    const valid = parsed.filter((p): p is PlatformId =>
      (ALL_IDS as string[]).includes(p as string),
    );
    return valid.length > 0 ? valid : DEFAULT;
  } catch {
    return DEFAULT;
  }
}

export function PlatformFilterProvider({ children }: { children: ReactNode }) {
  const [platforms, setPlatforms] = useState<PlatformId[]>(loadInitial);

  useEffect(() => {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(platforms));
    } catch {
      /* ignore quota / privacy-mode errors */
    }
  }, [platforms]);

  const toggle = useCallback((p: PlatformId) => {
    setPlatforms((prev) => {
      if (prev.includes(p)) {
        // Never let the selection become empty.
        if (prev.length === 1) return prev;
        return prev.filter((x) => x !== p);
      }
      // Preserve canonical order so the param string is stable.
      return ALL_IDS.filter((x) => prev.includes(x) || x === p);
    });
  }, []);

  const isSelected = useCallback(
    (p: PlatformId) => platforms.includes(p),
    [platforms],
  );

  const param = useMemo(() => {
    if (platforms.length >= ALL_IDS.length) return "";
    return platforms.join(",");
  }, [platforms]);

  const withParam = useCallback(
    (url: string) => {
      if (!param) return url;
      const sep = url.includes("?") ? "&" : "?";
      return `${url}${sep}platforms=${encodeURIComponent(param)}`;
    },
    [param],
  );

  const value = useMemo<PlatformFilterValue>(
    () => ({ platforms, isSelected, toggle, param, withParam }),
    [platforms, isSelected, toggle, param, withParam],
  );

  return (
    <PlatformFilterContext.Provider value={value}>
      {children}
    </PlatformFilterContext.Provider>
  );
}

export function usePlatformFilter(): PlatformFilterValue {
  const ctx = useContext(PlatformFilterContext);
  if (!ctx) {
    throw new Error(
      "usePlatformFilter must be used inside <PlatformFilterProvider>",
    );
  }
  return ctx;
}
