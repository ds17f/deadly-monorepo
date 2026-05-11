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

export interface WatchedInstall {
  iid: string;
  name: string | null;
  notes: string | null;
  watched_at: number;
}

interface WatchedContextValue {
  /** Lookup by iid. Always reflects latest server state. */
  watched: Map<string, WatchedInstall>;
  isWatched: (iid: string) => boolean;
  nameFor: (iid: string) => string | null;
  /** Upsert a watch flag for `iid`. name/notes may be null. */
  setWatched: (
    iid: string,
    name: string | null,
    notes: string | null,
  ) => Promise<void>;
  /** Remove the watch flag. */
  unwatch: (iid: string) => Promise<void>;
  refresh: () => Promise<void>;
}

const WatchedInstallsContext = createContext<WatchedContextValue | null>(null);

export function WatchedInstallsProvider({ children }: { children: ReactNode }) {
  const [list, setList] = useState<WatchedInstall[]>([]);

  const refresh = useCallback(async () => {
    try {
      const res = await fetch("/api/analytics/watched", {
        credentials: "include",
      });
      if (!res.ok) return;
      const body = (await res.json()) as { watched: WatchedInstall[] };
      setList(body.watched);
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const watched = useMemo(() => {
    const m = new Map<string, WatchedInstall>();
    for (const w of list) m.set(w.iid, w);
    return m;
  }, [list]);

  const isWatched = useCallback((iid: string) => watched.has(iid), [watched]);
  const nameFor = useCallback(
    (iid: string) => watched.get(iid)?.name ?? null,
    [watched],
  );

  const setWatched = useCallback(
    async (iid: string, name: string | null, notes: string | null) => {
      const res = await fetch(
        `/api/analytics/watched/${encodeURIComponent(iid)}`,
        {
          method: "PUT",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name, notes }),
        },
      );
      if (!res.ok) return;
      const updated = (await res.json()) as WatchedInstall;
      setList((prev) => {
        const next = prev.filter((w) => w.iid !== iid);
        next.unshift(updated);
        return next;
      });
    },
    [],
  );

  const unwatch = useCallback(async (iid: string) => {
    const res = await fetch(
      `/api/analytics/watched/${encodeURIComponent(iid)}`,
      {
        method: "DELETE",
        credentials: "include",
      },
    );
    if (!res.ok && res.status !== 204) return;
    setList((prev) => prev.filter((w) => w.iid !== iid));
  }, []);

  const value: WatchedContextValue = useMemo(
    () => ({ watched, isWatched, nameFor, setWatched, unwatch, refresh }),
    [watched, isWatched, nameFor, setWatched, unwatch, refresh],
  );

  return (
    <WatchedInstallsContext.Provider value={value}>
      {children}
    </WatchedInstallsContext.Provider>
  );
}

export function useWatchedInstalls(): WatchedContextValue {
  const ctx = useContext(WatchedInstallsContext);
  if (!ctx) {
    throw new Error(
      "useWatchedInstalls must be used inside <WatchedInstallsProvider>",
    );
  }
  return ctx;
}
