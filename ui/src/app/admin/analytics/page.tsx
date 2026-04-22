import { Suspense } from "react";
import { buildShowIndex, getShowById } from "@/lib/shows";
import AnalyticsDashboard from "./AnalyticsDashboard";

function loadShowNames(): Array<{ id: string; d: string; v: string; c: string; s: string; tc: number }> {
  try {
    return buildShowIndex().map((entry) => {
      let tc = 0;
      try {
        const show = getShowById(entry.id);
        if (show.setlist) {
          tc = show.setlist.reduce((sum, set) => sum + (set.songs?.length ?? 0), 0);
        }
      } catch { /* show file missing */ }
      return { id: entry.id, d: entry.d, v: entry.v, c: entry.c, s: entry.s, tc };
    });
  } catch {
    return [];
  }
}

export default function AnalyticsPage() {
  const showNames = loadShowNames();

  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
          <p className="text-zinc-400">Loading...</p>
        </div>
      }
    >
      <AnalyticsDashboard showNames={showNames} />
    </Suspense>
  );
}
