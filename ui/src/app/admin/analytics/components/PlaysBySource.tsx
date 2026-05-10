"use client";

interface SourceRow {
  source: string;
  plays: number;
  distinct_listeners: number;
}

const SOURCE_LABELS: Record<string, string> = {
  auto_advance: "Auto-advance",
  restore: "Cold-launch restore",
  browse: "Show detail",
  library_favorites: "Favorites",
  deeplink: "Deep link",
  search_result: "Search result",
  user: "User",
  user_play: "User play",
  "(unattributed)": "Unattributed (legacy)",
};

export default function PlaysBySource({
  data,
  onSourceClick,
}: {
  data: SourceRow[];
  onSourceClick?: (source: string) => void;
}) {
  if (data.length === 0) {
    return (
      <p className="text-sm text-zinc-500 italic">
        No playback_start events in the window.
      </p>
    );
  }

  const total = data.reduce((sum, r) => sum + r.plays, 0);
  return (
    <div className="space-y-1">
      {data.map((r) => {
        const rate = total === 0 ? 0 : r.plays / total;
        const pct = Math.round(rate * 100);
        return (
          <div
            key={r.source}
            onClick={() => onSourceClick?.(r.source)}
            className={`flex items-baseline gap-3 bg-deadly-surface rounded-lg p-3 ${
              onSourceClick ? "cursor-pointer hover:bg-zinc-700/50 transition-colors" : ""
            }`}
          >
            <div className="min-w-0 flex-1">
              <p className="text-sm text-zinc-200">
                {SOURCE_LABELS[r.source] ?? r.source}
              </p>
              <div className="mt-1.5 h-1.5 w-full bg-zinc-800 rounded-full overflow-hidden">
                <div
                  className="h-full bg-deadly-blue rounded-full"
                  style={{ width: `${pct}%` }}
                />
              </div>
            </div>
            <div className="flex flex-col items-end tabular-nums flex-shrink-0">
              <span className="text-sm text-zinc-300">{pct}%</span>
              <span className="text-xs text-zinc-500">
                {r.plays} play{r.plays !== 1 ? "s" : ""} · {r.distinct_listeners}{" "}
                listener{r.distinct_listeners !== 1 ? "s" : ""}
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
