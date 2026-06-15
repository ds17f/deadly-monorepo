"use client";

import Link from "next/link";
import { useState } from "react";
import { useUserData } from "@/contexts/UserDataContext";
import { useAuth } from "@/contexts/AuthContext";
import { formatShowDate, formatLocation } from "@/components/show/showFormat";
import type { BacklogItem } from "@/types/userdata";

// The Show Queue as a reorderable list. Click a row to open the show (play
// from there or let Autoplay's Show Queue mode feed it). Reorder with the
// up/down controls; remove with the ✕. Server-backed via useUserData, shared
// with the mobile apps.
export default function QueueTab() {
  const { user } = useAuth();
  const { backlog, removeFromQueue, reorderQueue } = useUserData();

  if (!user) {
    return <p className="text-sm text-white/40">Sign in to use your Show Queue.</p>;
  }

  if (backlog.length === 0) {
    return (
      <div className="rounded-lg border border-white/10 p-8 text-center">
        <p className="text-white/70">Your Show Queue is empty</p>
        <p className="mt-1 text-sm text-white/40">
          Add shows with the stacked-cards button on any show page; they play after the current one.
        </p>
      </div>
    );
  }

  const move = (index: number, dir: -1 | 1) => {
    const next = index + dir;
    if (next < 0 || next >= backlog.length) return;
    const ids = backlog.map((b) => b.showId);
    [ids[index], ids[next]] = [ids[next], ids[index]];
    reorderQueue(ids);
  };

  return (
    <div>
      <p className="mb-3 text-sm text-white/40">
        {backlog.length === 1 ? "1 show" : `${backlog.length} shows`}
      </p>
      <ul className="flex flex-col gap-1">
        {backlog.map((item, i) => (
          <QueueRow
            key={item.showId}
            item={item}
            isFirst={i === 0}
            isLast={i === backlog.length - 1}
            onUp={() => move(i, -1)}
            onDown={() => move(i, 1)}
            onRemove={() => removeFromQueue(item.showId)}
          />
        ))}
      </ul>
    </div>
  );
}

function QueueRow({
  item,
  isFirst,
  isLast,
  onUp,
  onDown,
  onRemove,
}: {
  item: BacklogItem;
  isFirst: boolean;
  isLast: boolean;
  onUp: () => void;
  onDown: () => void;
  onRemove: () => void;
}) {
  const [broken, setBroken] = useState(false);
  const src = item.image && !broken ? item.image : "/cover-fallback.png";
  const location = formatLocation(item);

  return (
    <li className="flex items-center gap-3 rounded-lg p-2 transition hover:bg-white/5">
      <Link href={`/shows/${item.showId}`} className="flex min-w-0 flex-1 items-center gap-3">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={src}
          alt=""
          loading="lazy"
          referrerPolicy="no-referrer"
          onError={() => setBroken(true)}
          className="h-12 w-12 flex-shrink-0 rounded-md bg-white/5 object-cover"
        />
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-white">{formatShowDate(item)}</p>
          {item.venue && <p className="truncate text-xs text-white/50">{item.venue}</p>}
          {location && <p className="truncate text-xs text-white/40">{location}</p>}
        </div>
      </Link>

      <div className="flex flex-shrink-0 items-center gap-1">
        <RowButton label="Move up" disabled={isFirst} onClick={onUp}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M18 15l-6-6-6 6" /></svg>
        </RowButton>
        <RowButton label="Move down" disabled={isLast} onClick={onDown}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M6 9l6 6 6-6" /></svg>
        </RowButton>
        <RowButton label="Remove from Show Queue" onClick={onRemove}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6L6 18M6 6l12 12" /></svg>
        </RowButton>
      </div>
    </li>
  );
}

function RowButton({
  label,
  disabled,
  onClick,
  children,
}: {
  label: string;
  disabled?: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      title={label}
      aria-label={label}
      className="inline-flex h-8 w-8 items-center justify-center rounded-full text-white/60 transition hover:bg-white/10 hover:text-white disabled:cursor-not-allowed disabled:opacity-30"
    >
      {children}
    </button>
  );
}
