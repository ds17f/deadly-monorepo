"use client";

import { useEffect, useMemo, useState } from "react";
import { fetchRecentShows } from "@/lib/userDataApi";
import type { RecentShow } from "@/types/userdata";
import LibraryView from "@/components/library/LibraryView";
import { recentToItem } from "@/components/library/libraryItem";

// Recently played as the full library surface. Share is the only per-row
// action — there is no delete-recent endpoint, so recents aren't removable
// here (see PLANS/mobile-server-sync.md).
export default function RecentTab() {
  const [state, setState] = useState<"loading" | "error" | "ready">("loading");
  const [shows, setShows] = useState<RecentShow[]>([]);

  useEffect(() => {
    let cancelled = false;
    fetchRecentShows()
      .then((s) => {
        if (cancelled) return;
        setShows(s);
        setState("ready");
      })
      .catch(() => {
        if (!cancelled) setState("error");
      });
    return () => {
      cancelled = true;
    };
  }, []);

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
