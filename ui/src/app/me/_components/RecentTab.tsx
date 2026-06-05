"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchRecentShows } from "@/lib/userDataApi";
import { useUserDataRefresh } from "@/lib/userDataEvents";
import type { RecentShow } from "@/types/userdata";
import LibraryView from "@/components/library/LibraryView";
import { recentToItem } from "@/components/library/libraryItem";

// Recently played as the full library surface. Share is the only per-row
// action — there is no delete-recent endpoint, so recents aren't removable
// here (see PLANS/mobile-server-sync.md). Auto-refreshes when a play records a
// recent (or on focus) so it updates without a reload, mirroring mobile.
export default function RecentTab() {
  const [state, setState] = useState<"loading" | "error" | "ready">("loading");
  const [shows, setShows] = useState<RecentShow[]>([]);

  const load = useCallback(() => {
    fetchRecentShows()
      .then((s) => {
        setShows(s);
        setState("ready");
      })
      // Don't flash an error over an already-loaded list on a transient refetch.
      .catch(() => setState((prev) => (prev === "ready" ? "ready" : "error")));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useUserDataRefresh(load);

  const items = useMemo(() => shows.map(recentToItem), [shows]);

  return (
    <LibraryView
      kind="recent"
      loadState={state}
      items={items}
      emptyTitle="Recent shows"
      emptyHint="Play something on any device to fill this in."
      actions={{}}
    />
  );
}
