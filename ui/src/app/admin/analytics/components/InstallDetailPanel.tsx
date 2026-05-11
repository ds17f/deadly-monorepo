"use client";

import { useEffect, useMemo, useState } from "react";
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
  zIndexBase?: number;
  onClose: () => void;
}

export default function InstallDetailPanel({ iid, zIndexBase = 60, onClose }: Props) {
  const [data, setData] = useState<InstallData | null>(null);
  const [loading, setLoading] = useState(false);
  const [eventFilter, setEventFilter] = useState<string | null>(null);

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
        // ignore
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [iid]);

  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [onClose]);

  // Lock body scroll while panel is open so the page behind doesn't scroll
  // when the user tries to scroll the panel content.
  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, []);

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
    <>
      <div
        className="fixed inset-0 bg-black/50"
        style={{ zIndex: zIndexBase }}
        onClick={onClose}
      />
      <div
        className="fixed bg-deadly-surface flex flex-col
          inset-x-0 bottom-0 h-[85vh] rounded-t-2xl
          sm:inset-x-auto sm:right-0 sm:top-0 sm:bottom-0 sm:h-full sm:w-[560px] sm:max-w-[100vw] sm:rounded-none sm:rounded-l-2xl
        "
        style={{ zIndex: zIndexBase + 1 }}
      >
        <div className="sm:hidden flex justify-center py-2">
          <div className="w-10 h-1 bg-zinc-600 rounded-full" />
        </div>

        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-zinc-700 flex-shrink-0">
          <h2 className="text-base font-semibold text-white truncate flex items-center gap-2 min-w-0">
            <span>{emojiForId(iid)}</span>
            <span className="font-mono text-sm text-zinc-300">{iid.slice(0, 8)}</span>
            <span className="text-zinc-500 text-xs font-normal">install</span>
          </h2>
          <button
            onClick={onClose}
            className="text-zinc-400 hover:text-white text-xl leading-none flex-shrink-0"
            aria-label="Close"
          >
            &times;
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto overflow-x-hidden overscroll-contain">
          {loading && !data ? (
            <div className="flex items-center justify-center p-12">
              <p className="text-zinc-400">Loading events...</p>
            </div>
          ) : data ? (
            <div className="p-4">
              {/* Metadata */}
              <div className="grid grid-cols-2 gap-3 mb-4">
                <Meta label="Platform" value={data.platform} />
                <Meta label="Version" value={data.app_version} />
                <Meta label="First Seen" value={data.first_seen} />
                <Meta label="Last Seen" value={data.last_seen} />
              </div>

              {/* Event filter */}
              <div className="flex items-center justify-between mb-3">
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
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>
              </div>

              <InstallEventTimeline events={filteredEvents} />
            </div>
          ) : (
            <div className="flex items-center justify-center p-12">
              <p className="text-zinc-500">No data</p>
            </div>
          )}
        </div>
      </div>
    </>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-zinc-500 uppercase tracking-wider">{label}</p>
      <p className="text-sm text-white">{value}</p>
    </div>
  );
}
