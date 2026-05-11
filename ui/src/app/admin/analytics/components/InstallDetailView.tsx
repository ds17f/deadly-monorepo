"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { emojiForId } from "./emojiId";
import InstallEventTimeline, { InstallEvent } from "./InstallEventTimeline";
import InstallOverview from "./InstallOverview";
import WatchToggle from "./WatchToggle";
import { useWatchedInstalls } from "./WatchedInstallsContext";

interface InstallData {
  iid: string;
  platform: string;
  app_version: string;
  first_seen: string;
  last_seen: string;
  total_events: number;
  events: InstallEvent[];
}

interface ShowName {
  id: string;
  d: string;
  v: string;
  c: string;
  s: string;
  tc: number;
  img: string | null;
}

interface Props {
  iid: string;
  backHref: string;
  showMap?: Map<string, ShowName>;
}

type Tab = "overview" | "events";

export default function InstallDetailView({ iid, backHref, showMap }: Props) {
  const router = useRouter();
  const { isWatched, nameFor } = useWatchedInstalls();
  const watched = isWatched(iid);
  const friendlyName = nameFor(iid);
  const [data, setData] = useState<InstallData | null>(null);
  const [loading, setLoading] = useState(false);
  const [tab, setTab] = useState<Tab>("overview");
  const [eventFilter, setEventFilter] = useState<string | null>(null);

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [iid]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setData(null);
    setEventFilter(null);
    setTab("overview");
    fetch(`/api/analytics/install/${encodeURIComponent(iid)}`, {
      credentials: "include",
    })
      .then((r) => (r.ok ? r.json() : null))
      .then((d: InstallData | null) => {
        if (!cancelled) setData(d);
      })
      .catch(() => {
        /* ignore */
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [iid]);

  const filteredEvents = useMemo(() => {
    if (!data) return [];
    if (!eventFilter) return data.events;
    return data.events.filter((e) => e.event === eventFilter);
  }, [data, eventFilter]);

  const eventTypes = useMemo(() => {
    if (!data) return [];
    return Array.from(new Set(data.events.map((e) => e.event))).sort();
  }, [data]);

  return (
    <div className="min-h-screen bg-deadly-bg">
      <div className="max-w-3xl mx-auto p-4 sm:p-6">
        {/* Header */}
        <div className="mb-3 flex items-center gap-3">
          <button
            onClick={() => router.push(backHref)}
            className="text-zinc-400 hover:text-white text-sm flex items-center gap-1"
          >
            <span>&larr;</span> Back
          </button>
        </div>
        <div className="flex items-start justify-between gap-3 mb-4 flex-wrap">
          <h1 className="text-lg font-semibold text-white flex items-center gap-2 flex-wrap min-w-0">
            {watched && <span className="text-amber-400">★</span>}
            <span className="text-xl">{emojiForId(iid)}</span>
            {friendlyName ? (
              <>
                <span className="text-zinc-100">{friendlyName}</span>
                <span className="font-mono text-xs text-zinc-500 [overflow-wrap:anywhere]">
                  {iid}
                </span>
              </>
            ) : (
              <span className="font-mono text-sm text-zinc-300 [overflow-wrap:anywhere]">
                {iid}
              </span>
            )}
          </h1>
          <WatchToggle iid={iid} />
        </div>

        {/* Tabs */}
        <div className="flex items-center gap-1 mb-4 border-b border-zinc-800">
          <TabButton active={tab === "overview"} onClick={() => setTab("overview")}>
            Overview
          </TabButton>
          <TabButton active={tab === "events"} onClick={() => setTab("events")}>
            Events
            {data && (
              <span className="ml-1 text-zinc-600 tabular-nums">
                {data.total_events}
              </span>
            )}
          </TabButton>
        </div>

        {loading && !data ? (
          <p className="text-zinc-400 py-8 text-center">Loading…</p>
        ) : !data ? (
          <p className="text-zinc-500 py-8 text-center">No data</p>
        ) : tab === "overview" ? (
          <InstallOverview data={data} showMap={showMap} />
        ) : (
          <>
            <div className="flex items-center justify-between mb-3 gap-2">
              <span className="text-xs text-zinc-500">
                {filteredEvents.length} event
                {filteredEvents.length !== 1 ? "s" : ""}
              </span>
              <select
                value={eventFilter ?? ""}
                onChange={(e) => setEventFilter(e.target.value || null)}
                className="bg-zinc-800 text-xs text-zinc-300 rounded px-2 py-1 border border-zinc-700"
              >
                <option value="">All events</option>
                {eventTypes.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <InstallEventTimeline events={filteredEvents} />
          </>
        )}
      </div>
    </div>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={`px-3 py-1.5 text-sm border-b-2 -mb-px transition-colors ${
        active
          ? "border-deadly-blue text-white"
          : "border-transparent text-zinc-400 hover:text-zinc-200"
      }`}
    >
      {children}
    </button>
  );
}
