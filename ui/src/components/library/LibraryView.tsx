"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { type FilterNode, selectedYears } from "./DecadeCascadeFilter";
import LibraryToolbar from "./LibraryToolbar";
import LibraryGridCard from "./LibraryGridCard";
import RowActionsMenu from "./RowActionsMenu";
import RowActionsBar from "./RowActionsBar";
import {
  type LibraryItem,
  type LibraryKind,
  type SortDir,
  SORTS_BY_KIND,
  matchesQuery,
  sortItems,
} from "./libraryItem";

export interface LibraryActions {
  canPin?: boolean;
  // Called after the local optimistic flip; performs the persistent write.
  onPinToggle?: (item: LibraryItem, nextPinned: boolean) => void;
  remove?: {
    label: string;
    confirmTitle: string;
    confirmMessage?: (item: LibraryItem) => string;
    onRemove: (item: LibraryItem) => void;
  };
}

const VIEW_KEY = "me:libraryView";

// Slightly-larger square cover for list rows (the list pane is wide). Own
// fallback so it can size independently of the fixed-size ShowArtwork.
function Cover({ image, alt }: { image?: string | null; alt: string }) {
  const [errored, setErrored] = useState(false);
  const src = errored || !image ? "/cover-fallback.png" : image;
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt={alt}
      loading="lazy"
      referrerPolicy="no-referrer"
      onError={() => setErrored(true)}
      className="h-[72px] w-[72px] flex-shrink-0 rounded-lg bg-white/5 object-cover"
    />
  );
}

/**
 * The shared body of the three /me library tabs. The tab fetches + adapts its
 * rows into LibraryItem[] and passes them in; this owns search / sort / filter
 * / list-grid state, renders the toolbar, and wires per-row actions. Pin and
 * remove update the local list optimistically, then call the tab's persistent
 * write via `actions`.
 */
export default function LibraryView({
  kind,
  loadState,
  items,
  emptyTitle,
  emptyHint,
  actions,
}: {
  kind: LibraryKind;
  loadState: "loading" | "error" | "ready";
  items: LibraryItem[];
  emptyTitle: string;
  emptyHint: string;
  actions?: LibraryActions;
}) {
  const sorts = SORTS_BY_KIND[kind];
  const [local, setLocal] = useState<LibraryItem[]>(items);
  const [query, setQuery] = useState("");
  const [sortId, setSortId] = useState(sorts[0].id);
  const [dir, setDir] = useState<SortDir>("desc");
  // Default to grid for a stable first render (static export), then adopt the
  // persisted choice on the client to avoid a hydration mismatch.
  const [view, setView] = useState<"list" | "grid">("grid");
  const [viewLoaded, setViewLoaded] = useState(false);
  const [path, setPath] = useState<FilterNode[]>([]);

  useEffect(() => {
    if (window.localStorage.getItem(VIEW_KEY) === "list") setView("list");
    setViewLoaded(true);
  }, []);

  // Mirror freshly-fetched rows into local state (the source of optimistic
  // pin/remove edits).
  useEffect(() => {
    setLocal(items);
  }, [items]);

  useEffect(() => {
    // Don't write until we've read, so the default doesn't clobber the stored
    // value on first mount.
    if (viewLoaded) window.localStorage.setItem(VIEW_KEY, view);
  }, [view, viewLoaded]);

  const years = useMemo(() => selectedYears(path), [path]);

  const visible = useMemo(() => {
    const filtered = local.filter(
      (it) =>
        matchesQuery(it, query) &&
        (years == null || (it.year != null && years.has(it.year))),
    );
    return sortItems(filtered, sortId, dir);
  }, [local, query, years, sortId, dir]);

  // Shared action props for a row — rendered as a "⋯" menu in grid view and as
  // an inline icon bar in list view.
  function actionProps(item: LibraryItem) {
    if (!actions) return null;
    const remove = actions.remove;
    return {
      showId: item.showId,
      recordingId: item.bestRecordingId,
      shareSubtitle: item.venue
        ? `${item.dateLabel} · ${item.venue}`
        : item.dateLabel,
      canPin: actions.canPin,
      pinned: item.pinned,
      onPinToggle: () => {
        const next = !item.pinned;
        setLocal((prev) =>
          prev.map((x) =>
            x.showId === item.showId ? { ...x, pinned: next } : x,
          ),
        );
        actions.onPinToggle?.(item, next);
      },
      remove: remove
        ? {
            label: remove.label,
            confirmTitle: remove.confirmTitle,
            confirmMessage: remove.confirmMessage?.(item),
            onConfirm: () => {
              setLocal((prev) => prev.filter((x) => x.showId !== item.showId));
              remove.onRemove(item);
            },
          }
        : undefined,
    };
  }

  if (loadState === "loading") {
    return (
      <div className="grid grid-cols-2 gap-x-5 gap-y-7 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
        {Array.from({ length: 8 }).map((_, i) => (
          <div
            key={i}
            className="aspect-square animate-pulse rounded-lg bg-deadly-surface"
          />
        ))}
      </div>
    );
  }

  if (loadState === "error") {
    return (
      <div className="rounded-lg border border-white/10 bg-deadly-surface p-8 text-center">
        <p className="text-sm text-white/50">
          Couldn&apos;t load this list. Try again in a moment.
        </p>
      </div>
    );
  }

  if (local.length === 0) {
    return (
      <div className="rounded-lg border border-white/10 bg-deadly-surface p-8 text-center">
        <h2 className="text-lg font-medium text-white">{emptyTitle}</h2>
        <p className="mx-auto mt-2 max-w-md text-sm text-white/50">{emptyHint}</p>
      </div>
    );
  }

  return (
    <div>
      <LibraryToolbar
        query={query}
        onQuery={setQuery}
        sorts={sorts}
        sortId={sortId}
        onSortId={setSortId}
        dir={dir}
        onDir={setDir}
        view={view}
        onView={setView}
        count={visible.length}
        path={path}
        onPath={setPath}
      />

      {visible.length === 0 ? (
        <p className="p-6 text-center text-sm text-white/40">
          Nothing matches your search or filter.
        </p>
      ) : view === "grid" ? (
        <div className="grid grid-cols-2 gap-x-5 gap-y-7 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
          {visible.map((it) => {
            const p = actionProps(it);
            return (
              <LibraryGridCard
                key={it.showId}
                item={it}
                menu={p ? <RowActionsMenu {...p} /> : null}
              />
            );
          })}
        </div>
      ) : (
        <ul className="space-y-2.5">
          {visible.map((it) => (
            <li
              key={it.showId}
              className="flex items-center gap-4 rounded-xl border border-white/10 bg-deadly-surface p-3 transition hover:border-white/30"
            >
              <Link
                href={`/shows/${it.showId}`}
                className="flex min-w-0 flex-1 items-center gap-4"
              >
                <Cover image={it.image} alt={it.dateLabel} />
                <div className="min-w-0 flex-1">
                  <p className="flex items-center gap-1.5 truncate text-lg font-semibold text-white">
                    {it.pinned && (
                      <svg
                        width="16"
                        height="16"
                        viewBox="0 0 24 24"
                        fill="currentColor"
                        className="flex-shrink-0 text-deadly-accent"
                        aria-label="Pinned"
                      >
                        <path d="M16 3v2l-1 1v5l3 3v2h-5v6h-2v-6H4v-2l3-3V6L6 5V3z" />
                      </svg>
                    )}
                    {it.rating != null && (
                      <span className="flex-shrink-0 text-yellow-400">★</span>
                    )}
                    <span className="truncate">{it.dateLabel}</span>
                  </p>
                  {it.location && (
                    <p className="truncate text-base text-white/60">{it.location}</p>
                  )}
                  {it.venue && (
                    <p className="truncate text-sm text-white/40">{it.venue}</p>
                  )}
                </div>
              </Link>
              {(() => {
                const p = actionProps(it);
                if (!p) return null;
                return (
                  <>
                    {/* Mobile: a compact "⋯" menu so the row text gets the
                        space instead of a strip of icons. */}
                    <div className="flex-shrink-0 sm:hidden">
                      <RowActionsMenu {...p} />
                    </div>
                    {/* Desktop: discrete inline action icons (room to spare). */}
                    <div className="hidden sm:flex">
                      <RowActionsBar {...p} />
                    </div>
                  </>
                );
              })()}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
