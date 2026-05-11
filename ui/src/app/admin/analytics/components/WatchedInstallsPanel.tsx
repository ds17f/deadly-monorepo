"use client";

import { emojiForId } from "./emojiId";
import { useWatchedInstalls } from "./WatchedInstallsContext";

function relativeAge(ts: number): string {
  const ms = Date.now() - ts;
  if (ms < 60_000) return `${Math.floor(ms / 1000)}s ago`;
  if (ms < 3_600_000) return `${Math.floor(ms / 60_000)}m ago`;
  if (ms < 86_400_000) return `${Math.floor(ms / 3_600_000)}h ago`;
  return `${Math.floor(ms / 86_400_000)}d ago`;
}

export default function WatchedInstallsPanel({
  onOpenInstall,
}: {
  onOpenInstall?: (iid: string) => void;
}) {
  const { watched, unwatch } = useWatchedInstalls();
  const list = Array.from(watched.values()).sort(
    (a, b) => b.watched_at - a.watched_at,
  );

  if (list.length === 0) {
    return (
      <p className="text-sm text-zinc-500 italic">
        No watched installs. Open any install detail page and click ☆ Watch to
        flag it.
      </p>
    );
  }

  return (
    <div className="space-y-1">
      {list.map((w) => (
        <div
          key={w.iid}
          className="bg-deadly-surface rounded-lg px-3 py-2 flex items-center gap-3 ring-1 ring-amber-500/40"
        >
          <span className="text-amber-400">★</span>
          <span>{emojiForId(w.iid)}</span>
          <button
            onClick={() => onOpenInstall?.(w.iid)}
            className="flex-1 min-w-0 text-left hover:text-deadly-blue transition-colors"
          >
            {w.name ? (
              <>
                <p className="text-sm text-zinc-100 truncate">{w.name}</p>
                <p className="font-mono text-xs text-zinc-500 truncate">
                  {w.iid.slice(0, 16)}…
                </p>
              </>
            ) : (
              <p className="font-mono text-sm text-zinc-300 truncate">
                {w.iid.slice(0, 16)}…
              </p>
            )}
          </button>
          <span className="text-xs text-zinc-500 tabular-nums shrink-0">
            flagged {relativeAge(w.watched_at)}
          </span>
          <button
            onClick={() => unwatch(w.iid)}
            className="text-xs text-zinc-500 hover:text-red-400 shrink-0"
            title="Stop watching"
          >
            unwatch
          </button>
        </div>
      ))}
    </div>
  );
}
