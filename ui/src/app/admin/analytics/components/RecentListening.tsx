"use client";

import { useEffect, useState } from "react";
import { ListeningRow, type TrackPlay } from "./listeningRow";
import { useWatchedInstalls } from "./WatchedInstallsContext";

const INITIAL_VISIBLE = 10;

interface RecentSession {
  iid: string;
  platform: string;
  app_version: string;
  started_at: number;
  last_event_at: number;
  show_id: string | null;
  recording_id: string | null;
  track_index: number | null;
  source: string | null;
  tracks: TrackPlay[];
  session_count: number;
  ended: boolean;
}

interface ShowName {
  id: string;
  d: string;
  v: string;
  c: string;
  s: string;
  tc: number;
}

const POLL_INTERVAL_MS = 60_000;

export default function RecentListening({
  showMap,
  onOpenInstall,
  watchedOnly = false,
}: {
  showMap?: Map<string, ShowName>;
  onOpenInstall?: (iid: string) => void;
  watchedOnly?: boolean;
} = {}) {
  const { isWatched } = useWatchedInstalls();
  const [sessions, setSessions] = useState<RecentSession[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(false);

  const fetchData = async () => {
    try {
      const res = await fetch(
        "/api/analytics/recent-listening?hours=24",
        { credentials: "include" },
      );
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const body = (await res.json()) as { sessions: RecentSession[] };
      setSessions(body.sessions);
      setError(null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Failed to load");
    }
  };

  useEffect(() => {
    fetchData();
    const id = setInterval(fetchData, POLL_INTERVAL_MS);
    return () => clearInterval(id);
  }, []);

  if (error)
    return (
      <p className="text-sm text-red-400">Recent Listening error: {error}</p>
    );
  if (sessions === null)
    return <p className="text-sm text-zinc-500">Loading…</p>;

  if (sessions.length === 0) {
    return (
      <p className="text-sm text-zinc-500 italic">
        No completed listening sessions in the last 24 hours.
      </p>
    );
  }

  const filtered = watchedOnly
    ? sessions.filter((s) => isWatched(s.iid))
    : sessions;
  const visible = expanded ? filtered : filtered.slice(0, INITIAL_VISIBLE);
  const hiddenCount = filtered.length - visible.length;

  if (filtered.length === 0) {
    return (
      <p className="text-sm text-zinc-500 italic">
        {watchedOnly
          ? "No watched installs have played in the last 24h."
          : "No completed listening sessions in the last 24 hours."}
      </p>
    );
  }

  return (
    <div className="space-y-1">
      <p className="text-xs text-zinc-500 mb-2">
        {filtered.length} session{filtered.length !== 1 ? "s" : ""} in the last 24h
        {watchedOnly ? " (watched only)" : ""}
      </p>
      {visible.map((s) => (
        <ListeningRow
          key={`${s.iid}-${s.started_at}`}
          rowKey={`${s.iid}-${s.started_at}`}
          iid={s.iid}
          platform={s.platform}
          app_version={s.app_version}
          ts={s.last_event_at}
          show_id={s.show_id}
          track_index={s.track_index}
          source={s.source}
          tracks={s.tracks}
          totalTracks={s.show_id ? showMap?.get(s.show_id)?.tc : undefined}
          onClick={onOpenInstall ? () => onOpenInstall(s.iid) : undefined}
        />
      ))}
      {filtered.length > INITIAL_VISIBLE && (
        <button
          onClick={() => setExpanded((v) => !v)}
          className="w-full text-xs text-zinc-400 hover:text-white py-2 mt-1 transition-colors"
        >
          {expanded ? "Show less" : `Show ${hiddenCount} more`}
        </button>
      )}
    </div>
  );
}
