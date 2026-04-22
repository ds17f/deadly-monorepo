import { Suspense } from "react";
import { buildShowIndex } from "@/lib/shows";
import AnalyticsDashboard from "./AnalyticsDashboard";

function loadShowNames(): Array<{ id: string; d: string; v: string; c: string; s: string }> {
  try {
    return buildShowIndex().map((s) => ({
      id: s.id,
      d: s.d,
      v: s.v,
      c: s.c,
      s: s.s,
    }));
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
