"use client";

import { ReactNode, useMemo, useState } from "react";

const SHOW_ID_RE = /^\d{4}-\d{2}-\d{2}$/;

function isShowId(value: unknown): value is string {
  return typeof value === "string" && SHOW_ID_RE.test(value);
}

function ShowLink({ id }: { id: string }) {
  return (
    <a
      href={`/shows/${id}`}
      target="_blank"
      rel="noopener noreferrer"
      onClick={(e) => e.stopPropagation()}
      className="text-deadly-blue hover:text-white underline decoration-dotted underline-offset-2 transition-colors"
    >
      {id}
    </a>
  );
}

export interface InstallEvent {
  id: number;
  event: string;
  ts: number;
  sid: string;
  platform: string;
  app_version: string;
  props: string | null;
}

type Category = "session" | "playback" | "feature" | "search" | "error" | "other";

interface CategoryStyle {
  edge: string;
  label: string;
  text: string;
}

const CATEGORY_STYLES: Record<Category, CategoryStyle> = {
  session: { edge: "bg-zinc-500", label: "session", text: "text-zinc-300" },
  playback: { edge: "bg-emerald-500", label: "playback", text: "text-emerald-300" },
  feature: { edge: "bg-deadly-blue", label: "feature", text: "text-deadly-blue" },
  search: { edge: "bg-purple-500", label: "search", text: "text-purple-300" },
  error: { edge: "bg-red-500", label: "error", text: "text-red-300" },
  other: { edge: "bg-zinc-600", label: "other", text: "text-zinc-400" },
};

function categoryFor(eventName: string): Category {
  if (eventName === "app_open" || eventName === "session_start" || eventName === "session_stop") return "session";
  if (eventName === "playback_start" || eventName === "playback_end") return "playback";
  if (eventName === "feature_use") return "feature";
  if (eventName === "search") return "search";
  if (eventName === "error") return "error";
  return "other";
}

function parseProps(propsStr: string | null): Record<string, unknown> {
  if (!propsStr) return {};
  try {
    return JSON.parse(propsStr) as Record<string, unknown>;
  } catch {
    return {};
  }
}

function formatMs(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  const s = Math.round(ms / 1000);
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60);
  const rs = s % 60;
  if (m < 60) return rs ? `${m}m ${rs}s` : `${m}m`;
  const h = Math.floor(m / 60);
  const rm = m % 60;
  return rm ? `${h}h ${rm}m` : `${h}h`;
}

function relativeTime(ts: number): string {
  const ms = Date.now() - ts;
  if (ms < 60_000) return "just now";
  if (ms < 3_600_000) return `${Math.floor(ms / 60_000)}m ago`;
  if (ms < 86_400_000) return `${Math.floor(ms / 3_600_000)}h ago`;
  return `${Math.floor(ms / 86_400_000)}d ago`;
}

function timeOfDay(ts: number): string {
  const d = new Date(ts);
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  const ss = String(d.getSeconds()).padStart(2, "0");
  return `${hh}:${mm}:${ss}`;
}

/**
 * Per-event one-line summary. Keep this terse — the full props always
 * remain available below as a fallback raw view. New event types fall
 * through to a generic "<event_name>" label.
 */
function eventSummary(evt: InstallEvent): ReactNode {
  const p = parseProps(evt.props);
  switch (evt.event) {
    case "app_open":
      return <span>App opened</span>;
    case "session_start":
      return <span>Session started</span>;
    case "session_stop": {
      const reason = typeof p.reason === "string" ? p.reason : null;
      return (
        <span>
          Session ended
          {reason && <span className="text-zinc-500"> · {reason}</span>}
        </span>
      );
    }
    case "playback_start": {
      const showId = p.show_id;
      const source = typeof p.source === "string" ? p.source : null;
      const track = p.track_index != null ? `track ${p.track_index}` : null;
      return (
        <span>
          {isShowId(showId) ? <ShowLink id={showId} /> : <span className="font-mono text-xs">{String(showId ?? "—")}</span>}
          {source && <span className="text-zinc-500"> · {source}</span>}
          {track && <span className="text-zinc-500"> · {track}</span>}
        </span>
      );
    }
    case "playback_end": {
      const showId = p.show_id;
      const listenedMs = typeof p.listened_ms === "number" ? p.listened_ms : null;
      const reason = typeof p.reason === "string" ? p.reason : null;
      return (
        <span>
          {isShowId(showId) ? <ShowLink id={showId} /> : <span className="font-mono text-xs">{String(showId ?? "—")}</span>}
          {listenedMs != null && <span className="text-zinc-500"> · {formatMs(listenedMs)}</span>}
          {reason && <span className="text-zinc-500"> · {reason}</span>}
        </span>
      );
    }
    case "feature_use": {
      const feature = typeof p.feature === "string" ? p.feature : "feature_use";
      const target = p.target_id;
      return (
        <span>
          <span>{feature}</span>
          {isShowId(target) && (
            <>
              <span className="text-zinc-500"> · </span>
              <ShowLink id={target} />
            </>
          )}
          {target != null && !isShowId(target) && (
            <span className="text-zinc-500 font-mono"> · {String(target)}</span>
          )}
        </span>
      );
    }
    case "search": {
      const query = typeof p.query === "string" ? p.query : null;
      const count = typeof p.result_count === "number" ? p.result_count : null;
      return (
        <span>
          {query ? <span>&ldquo;{query}&rdquo;</span> : <span className="text-zinc-500">search</span>}
          {count != null && <span className="text-zinc-500"> · {count} result{count === 1 ? "" : "s"}</span>}
        </span>
      );
    }
    case "error": {
      const source = typeof p.source === "string" ? p.source : null;
      const message = typeof p.message === "string" ? p.message : null;
      const trimmed = message && message.length > 80 ? message.slice(0, 77) + "…" : message;
      return (
        <span>
          {source && <span className="text-zinc-400">{source}</span>}
          {trimmed && <span className="text-zinc-500">{source ? " · " : ""}{trimmed}</span>}
          {!source && !trimmed && <span>error</span>}
        </span>
      );
    }
    default:
      return <span>{evt.event}</span>;
  }
}

function PropsRaw({ propsStr }: { propsStr: string }): ReactNode {
  let obj: Record<string, unknown>;
  try {
    obj = JSON.parse(propsStr);
  } catch {
    return propsStr;
  }
  const entries = Object.entries(obj);
  if (entries.length === 0) return null;
  return (
    <>
      {entries.map(([k, v], i) => {
        const value = String(v);
        const linkable = (k === "target_id" || k === "show_id") && isShowId(value);
        return (
          <span key={k}>
            {i > 0 && ", "}
            <span className="text-zinc-500">{k}=</span>
            {linkable ? <ShowLink id={value} /> : <span>{value}</span>}
          </span>
        );
      })}
    </>
  );
}

type TimelineItem =
  | { kind: "event"; ts: number; evt: InstallEvent }
  | { kind: "playback"; ts: number; start: InstallEvent | null; end: InstallEvent | null };

function timelineKey(item: TimelineItem, fallback: number): string {
  if (item.kind === "event") return `e${item.evt.id}`;
  const a = item.start?.id ?? "";
  const b = item.end?.id ?? "";
  return `p${a}-${b}-${fallback}`;
}

/**
 * Pair adjacent playback_start → playback_end within the same session into a
 * single timeline item, keyed on show_id + recording_id + track_index. Reduces
 * the visual noise of auto-advancing tracks. Unpaired starts/ends fall through
 * as standalone events. Caller passes events sorted by ts ascending.
 */
function buildTimeline(sortedEvents: InstallEvent[]): TimelineItem[] {
  const items: TimelineItem[] = [];
  const pairedIds = new Set<number>();

  const trackKey = (props: Record<string, unknown>) =>
    [props.show_id, props.recording_id, props.track_index].map((v) => String(v ?? "")).join("|");

  for (let i = 0; i < sortedEvents.length; i++) {
    const e = sortedEvents[i]!;
    if (pairedIds.has(e.id)) continue;
    if (e.event !== "playback_start") continue;

    const startProps = parseProps(e.props);
    const key = trackKey(startProps);

    // Look forward in same session for a matching playback_end. Stop at next
    // playback_start (any) — same track restart breaks pairing.
    let match: InstallEvent | null = null;
    for (let j = i + 1; j < sortedEvents.length; j++) {
      const c = sortedEvents[j]!;
      if (c.sid !== e.sid) break;
      if (c.event === "playback_start") break;
      if (c.event === "playback_end") {
        const endProps = parseProps(c.props);
        if (trackKey(endProps) === key) {
          match = c;
          break;
        }
      }
    }
    if (match) {
      pairedIds.add(e.id);
      pairedIds.add(match.id);
      items.push({ kind: "playback", ts: e.ts, start: e, end: match });
    }
  }

  for (const e of sortedEvents) {
    if (pairedIds.has(e.id)) continue;
    // Lone playback_end without a paired start — render as a one-sided playback
    // pair so it still gets the prettier UI (e.g. resumed playback after a
    // cold start where the start happened in a previous session).
    if (e.event === "playback_end") {
      items.push({ kind: "playback", ts: e.ts, start: null, end: e });
    } else {
      items.push({ kind: "event", ts: e.ts, evt: e });
    }
  }

  items.sort((a, b) => a.ts - b.ts);
  return items;
}

interface Session {
  sid: string;
  startTs: number;
  endTs: number;
  events: InstallEvent[];
  items: TimelineItem[];
  platform: string;
  appVersion: string;
}

function groupBySession(events: InstallEvent[]): Session[] {
  const bySid = new Map<string, InstallEvent[]>();
  for (const e of events) {
    const list = bySid.get(e.sid);
    if (list) list.push(e);
    else bySid.set(e.sid, [e]);
  }
  const sessions: Session[] = [];
  for (const [sid, list] of bySid) {
    const sorted = [...list].sort((a, b) => a.ts - b.ts);
    sessions.push({
      sid,
      events: sorted,
      items: buildTimeline(sorted),
      startTs: sorted[0]!.ts,
      endTs: sorted[sorted.length - 1]!.ts,
      platform: sorted[0]!.platform,
      appVersion: sorted[0]!.app_version,
    });
  }
  sessions.sort((a, b) => b.startTs - a.startTs);
  return sessions;
}

interface CompletionStyle {
  label: string;
  className: string;
}

const COMPLETION_STYLES: Record<string, CompletionStyle> = {
  track_complete: { label: "completed", className: "text-emerald-400" },
  next: { label: "skipped (next)", className: "text-amber-400" },
  prev: { label: "skipped (prev)", className: "text-amber-400" },
  pause: { label: "paused", className: "text-sky-300" },
  stop: { label: "stopped", className: "text-zinc-400" },
  error: { label: "error", className: "text-red-400" },
  session_stop: { label: "session ended", className: "text-zinc-500" },
};

const IN_PROGRESS_STYLE: CompletionStyle = {
  label: "in progress",
  className: "text-deadly-blue",
};

const FALLBACK_END_STYLE: CompletionStyle = {
  label: "ended",
  className: "text-zinc-400",
};

function PlaybackPairCard({
  start,
  end,
}: {
  start: InstallEvent | null;
  end: InstallEvent | null;
}) {
  const [expanded, setExpanded] = useState(false);
  const startProps = start ? parseProps(start.props) : {};
  const endProps = end ? parseProps(end.props) : {};
  const showId = startProps.show_id ?? endProps.show_id;
  const trackIndex = startProps.track_index ?? endProps.track_index;
  const source = typeof startProps.source === "string" ? startProps.source : null;
  const reasonRaw = typeof endProps.reason === "string" ? endProps.reason : null;
  const completion: CompletionStyle = !end
    ? IN_PROGRESS_STYLE
    : reasonRaw
    ? COMPLETION_STYLES[reasonRaw] ?? { label: reasonRaw, className: "text-zinc-400" }
    : FALLBACK_END_STYLE;
  const listenedMs =
    typeof endProps.listened_ms === "number" ? endProps.listened_ms : null;

  const tsForTime = start?.ts ?? end?.ts ?? 0;
  const style = CATEGORY_STYLES.playback;

  const hasExpandable =
    (start?.props && start.props !== "{}") || (end?.props && end.props !== "{}");

  return (
    <div className="relative pl-4">
      <div className={`absolute left-0 top-0 bottom-0 w-1 rounded-full ${style.edge}`} />
      <div
        className={`bg-zinc-800/50 rounded-lg p-2.5 ${
          hasExpandable ? "cursor-pointer hover:bg-zinc-800" : ""
        }`}
        onClick={hasExpandable ? () => setExpanded((v) => !v) : undefined}
      >
        <div className="flex items-baseline justify-between gap-2 mb-0.5">
          <span className={`text-xs uppercase tracking-wider ${style.text}`}>
            playback
          </span>
          <span className="text-xs text-zinc-500 font-mono flex-shrink-0">
            {timeOfDay(tsForTime)}
          </span>
        </div>
        <div className="text-sm text-zinc-200 break-words flex flex-wrap items-baseline gap-x-1">
          {isShowId(showId) ? (
            <ShowLink id={showId} />
          ) : (
            <span className="font-mono text-xs">{String(showId ?? "—")}</span>
          )}
          {trackIndex != null && (
            <span className="text-zinc-500">· track {String(trackIndex)}</span>
          )}
          {source && <span className="text-zinc-500">· {source}</span>}
          {listenedMs != null && (
            <span className="text-zinc-500">· {formatMs(listenedMs)}</span>
          )}
          <span className={`ml-auto text-xs font-medium ${completion.className}`}>
            {completion.label}
          </span>
        </div>
        {hasExpandable && expanded && (
          <div className="text-xs text-zinc-600 font-mono mt-2 break-all border-t border-zinc-700/50 pt-2 space-y-1">
            {start?.props && (
              <p>
                <span className="text-zinc-500">start: </span>
                <PropsRaw propsStr={start.props} />
              </p>
            )}
            {end?.props && (
              <p>
                <span className="text-zinc-500">end: </span>
                <PropsRaw propsStr={end.props} />
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function TimelineItemView({ item }: { item: TimelineItem }) {
  if (item.kind === "playback") {
    return <PlaybackPairCard start={item.start} end={item.end} />;
  }
  return <EventCard evt={item.evt} />;
}

function EventCard({ evt }: { evt: InstallEvent }) {
  const [expanded, setExpanded] = useState(false);
  const cat = categoryFor(evt.event);
  const style = CATEGORY_STYLES[cat];
  const hasProps = !!evt.props && evt.props !== "{}";

  return (
    <div className="relative pl-4">
      {/* Left edge color bar */}
      <div className={`absolute left-0 top-0 bottom-0 w-1 rounded-full ${style.edge}`} />
      <div
        className={`bg-zinc-800/50 rounded-lg p-2.5 ${hasProps ? "cursor-pointer hover:bg-zinc-800" : ""}`}
        onClick={hasProps ? () => setExpanded((v) => !v) : undefined}
      >
        <div className="flex items-baseline justify-between gap-2 mb-0.5">
          <span className={`text-xs uppercase tracking-wider ${style.text}`}>
            {evt.event}
          </span>
          <span className="text-xs text-zinc-500 font-mono flex-shrink-0">
            {timeOfDay(evt.ts)}
          </span>
        </div>
        <div className="text-sm text-zinc-200 break-words">{eventSummary(evt)}</div>
        {hasProps && expanded && evt.props && (
          <p className="text-xs text-zinc-600 font-mono mt-2 break-all border-t border-zinc-700/50 pt-2">
            <PropsRaw propsStr={evt.props} />
          </p>
        )}
      </div>
    </div>
  );
}

function SessionGroup({ session, defaultOpen }: { session: Session; defaultOpen: boolean }) {
  const [open, setOpen] = useState(defaultOpen);
  const duration = session.endTs - session.startTs;
  const dateStr = new Date(session.startTs).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
  });

  return (
    <div className="bg-zinc-900/40 rounded-lg overflow-hidden border border-zinc-800">
      <button
        onClick={() => setOpen((v) => !v)}
        className="w-full px-3 py-2 flex items-center justify-between gap-2 hover:bg-zinc-800/50 transition-colors"
      >
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-zinc-500 text-xs">{open ? "▾" : "▸"}</span>
          <span className="text-sm text-zinc-200">
            {dateStr} {timeOfDay(session.startTs)}
          </span>
          <span className="text-xs text-zinc-500">· {formatMs(duration)}</span>
          <span className="text-xs text-zinc-500">· {session.events.length} events</span>
        </div>
        <span className="text-xs text-zinc-500 flex-shrink-0">
          {relativeTime(session.startTs)}
        </span>
      </button>
      {open && (
        <div className="px-3 pb-3 pt-1 space-y-1.5 border-t border-zinc-800">
          {session.items.map((item, i) => (
            <TimelineItemView key={timelineKey(item, i)} item={item} />
          ))}
        </div>
      )}
    </div>
  );
}

interface Props {
  events: InstallEvent[];
}

export default function InstallEventTimeline({ events }: Props) {
  const [view, setView] = useState<"session" | "flat">("session");
  const sessions = useMemo(() => groupBySession(events), [events]);
  const flatItems = useMemo(() => {
    // Pair within each session, then merge and sort descending for flat view.
    const all: TimelineItem[] = [];
    for (const s of sessions) all.push(...s.items);
    all.sort((a, b) => b.ts - a.ts);
    return all;
  }, [sessions]);

  if (events.length === 0) {
    return <p className="text-center text-zinc-500 py-8">No events</p>;
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-end gap-1 text-xs">
        <button
          onClick={() => setView("session")}
          className={`px-2 py-1 rounded ${
            view === "session"
              ? "bg-zinc-700 text-white"
              : "text-zinc-500 hover:text-zinc-300"
          }`}
        >
          By session
        </button>
        <button
          onClick={() => setView("flat")}
          className={`px-2 py-1 rounded ${
            view === "flat"
              ? "bg-zinc-700 text-white"
              : "text-zinc-500 hover:text-zinc-300"
          }`}
        >
          Flat
        </button>
      </div>

      {view === "session" ? (
        <div className="space-y-2">
          {sessions.map((s, i) => (
            <SessionGroup key={s.sid} session={s} defaultOpen={i === 0} />
          ))}
        </div>
      ) : (
        <div className="space-y-1.5">
          {flatItems.map((item, i) => (
            <TimelineItemView key={timelineKey(item, i)} item={item} />
          ))}
        </div>
      )}
    </div>
  );
}
