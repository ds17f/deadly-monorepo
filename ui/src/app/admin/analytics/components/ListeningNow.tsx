"use client";

import { useEffect, useState } from "react";
import { ListeningRow, type TrackPlay } from "./listeningRow";
import { useWatchedInstalls } from "./WatchedInstallsContext";
import { usePlatformFilter } from "./PlatformFilterContext";

interface LiveListener {
  iid: string;
  platform: string;
  app_version: string;
  started_at: number;
  show_id: string | null;
  recording_id: string | null;
  track_index: number | null;
  source: string | null;
  tracks: TrackPlay[];
}

interface ShowName {
  id: string;
  d: string;
  v: string;
  c: string;
  s: string;
  tc: number;
}

const POLL_INTERVAL_MS = 15_000;

export default function ListeningNow({
  showMap,
  onOpenInstall,
  watchedOnly = false,
}: {
  showMap?: Map<string, ShowName>;
  onOpenInstall?: (iid: string) => void;
  watchedOnly?: boolean;
} = {}) {
  const { isWatched } = useWatchedInstalls();
  const { withParam, param } = usePlatformFilter();
  const [listeners, setListeners] = useState<LiveListener[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const fetchData = async () => {
    try {
      const res = await fetch(withParam("/api/analytics/live"), { credentials: "include" });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const body = (await res.json()) as { listeners: LiveListener[] };
      setListeners(body.listeners);
      setError(null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Failed to load");
    }
  };

  useEffect(() => {
    fetchData();
    const id = setInterval(fetchData, POLL_INTERVAL_MS);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [param]);

  if (error)
    return <p className="text-sm text-red-400">Listening Now error: {error}</p>;
  if (listeners === null)
    return <p className="text-sm text-zinc-500">Loading…</p>;

  const visible = watchedOnly
    ? listeners.filter((l) => isWatched(l.iid))
    : listeners;

  if (visible.length === 0) {
    return (
      <p className="text-sm text-zinc-500 italic">
        {watchedOnly
          ? "No watched installs are currently listening."
          : "No active sessions in the last 5 minutes."}
      </p>
    );
  }

  return (
    <div className="space-y-1">
      <p className="text-xs text-zinc-500 mb-2">
        {visible.length} active session{visible.length !== 1 ? "s" : ""}
        {watchedOnly ? " (watched only)" : " · window covers the longest Dead jams; killed sessions ghost up to 45 min"}
      </p>
      {visible.map((l) => (
        <ListeningRow
          key={`${l.iid}-${l.started_at}`}
          rowKey={`${l.iid}-${l.started_at}`}
          iid={l.iid}
          platform={l.platform}
          app_version={l.app_version}
          ts={l.started_at}
          show_id={l.show_id}
          track_index={l.track_index}
          source={l.source}
          tracks={l.tracks}
          totalTracks={l.show_id ? showMap?.get(l.show_id)?.tc : undefined}
          onClick={onOpenInstall ? () => onOpenInstall(l.iid) : undefined}
        />
      ))}
    </div>
  );
}
