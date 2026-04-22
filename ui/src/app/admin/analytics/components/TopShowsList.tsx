"use client";

interface ShowInfo {
  id: string;
  d: string;
  v: string;
  c: string;
  s: string;
}

interface TopShow {
  show_id: string;
  plays: number;
}

interface TopShowsListProps {
  shows: TopShow[];
  showMap: Map<string, ShowInfo>;
  onClick?: () => void;
  onShowClick?: (showId: string) => void;
}

function formatDate(d: string): string {
  const date = new Date(d + "T00:00:00");
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

export default function TopShowsList({ shows, showMap, onClick, onShowClick }: TopShowsListProps) {
  if (shows.length === 0) return null;

  return (
    <section
      className={`mb-6 ${onClick ? "cursor-pointer group" : ""}`}
      onClick={onClick}
    >
      <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3 group-hover:text-zinc-200 transition-colors">
        Top Shows (30d) {onClick && <span className="text-zinc-600">&rarr;</span>}
      </h2>
      <div className="space-y-1">
        {shows.map((show, i) => {
          const info = showMap.get(show.show_id);
          return (
            <div
              key={show.show_id}
              className="bg-deadly-surface rounded-lg p-3 flex items-start gap-3 hover:bg-zinc-700/50 transition-colors"
              onClick={(e) => {
                if (onShowClick) {
                  e.stopPropagation();
                  onShowClick(show.show_id);
                }
              }}
            >
              <span className="text-zinc-500 text-sm font-mono w-5 text-right flex-shrink-0 pt-0.5">
                {i + 1}
              </span>
              <div className="min-w-0 flex-1">
                {info ? (
                  <>
                    <p className="text-sm text-zinc-200">{formatDate(info.d)}</p>
                    <p className="text-xs text-zinc-400 truncate">
                      {info.v} &mdash; {info.c}, {info.s}
                    </p>
                  </>
                ) : (
                  <p className="text-sm text-zinc-300 font-mono">{show.show_id}</p>
                )}
              </div>
              <span className="text-sm text-zinc-400 flex-shrink-0 tabular-nums">
                {show.plays} play{show.plays !== 1 ? "s" : ""}
              </span>
            </div>
          );
        })}
      </div>
    </section>
  );
}
