"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { emojiForId } from "./emojiId";
import InstallEventTimeline, { InstallEvent } from "./InstallEventTimeline";

interface InstallData {
  iid: string;
  platform: string;
  app_version: string;
  first_seen: string;
  last_seen: string;
  total_events: number;
  events: InstallEvent[];
}

interface Props {
  iid: string;
  backHref: string;
}

export default function InstallDetailView({ iid, backHref }: Props) {
  const [data, setData] = useState<InstallData | null>(null);
  const [loading, setLoading] = useState(false);
  const [eventFilter, setEventFilter] = useState<string | null>(null);

  // Always start the detail page at the top — same pathname as the dashboard
  // means the browser doesn't reset scroll for us.
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [iid]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setData(null);
    setEventFilter(null);
    fetch(`/api/analytics/install/${encodeURIComponent(iid)}`, { credentials: "include" })
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
        <div className="mb-4 flex items-center gap-3">
          <Link
            href={backHref}
            className="text-zinc-400 hover:text-white text-sm flex items-center gap-1"
          >
            <span>&larr;</span> Back
          </Link>
        </div>
        <h1 className="text-lg font-semibold text-white flex items-center gap-2 mb-4 flex-wrap">
          <span className="text-xl">{emojiForId(iid)}</span>
          <span className="font-mono text-sm text-zinc-300 break-all">{iid}</span>
        </h1>

        {loading && !data ? (
          <p className="text-zinc-400 py-8 text-center">Loading events…</p>
        ) : data ? (
          <>
            {/* Metadata */}
            <div className="grid grid-cols-2 gap-3 mb-4">
              <Meta label="Platform" value={data.platform} />
              <Meta label="Version" value={data.app_version} />
              <Meta label="First Seen" value={data.first_seen} />
              <Meta label="Last Seen" value={data.last_seen} />
            </div>

            {/* Event filter */}
            <div className="flex items-center justify-between mb-3 gap-2">
              <span className="text-xs text-zinc-500">
                {filteredEvents.length} event{filteredEvents.length !== 1 ? "s" : ""}
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
        ) : (
          <p className="text-zinc-500 py-8 text-center">No data</p>
        )}
      </div>
    </div>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <p className="text-xs text-zinc-500 uppercase tracking-wider">{label}</p>
      <p className="text-sm text-white break-words">{value}</p>
    </div>
  );
}
