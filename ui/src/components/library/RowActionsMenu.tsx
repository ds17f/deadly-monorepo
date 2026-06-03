"use client";

import { useEffect, useRef, useState } from "react";
import ShareButton from "@/components/share/ShareButton";
import ConfirmDialog from "@/components/ui/ConfirmDialog";

export interface RemoveConfig {
  label: string;
  confirmTitle: string;
  confirmMessage?: string;
  onConfirm: () => void;
}

/**
 * A "⋯" overflow menu for one library row/card: Share, optional Pin/Unpin
 * (favorites), and an optional destructive Remove gated by a ConfirmDialog.
 * Self-contained — owns its dropdown, the ShareSheet, and the confirm modal.
 */
export default function RowActionsMenu({
  showId,
  recordingId,
  shareSubtitle,
  canPin = false,
  pinned = false,
  onPinToggle,
  remove,
}: {
  showId: string;
  recordingId?: string | null;
  shareSubtitle?: string;
  canPin?: boolean;
  pinned?: boolean;
  onPinToggle?: () => void;
  remove?: RemoveConfig;
}) {
  const [open, setOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, [open]);

  const itemClass =
    "block w-full rounded-md px-2 py-1.5 text-left text-sm text-white/80 transition hover:bg-white/10 hover:text-white";

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        aria-label="Show actions"
        className="flex h-8 w-8 items-center justify-center rounded-full bg-black/40 text-white/70 backdrop-blur transition hover:bg-black/60 hover:text-white"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
          <circle cx="5" cy="12" r="2" />
          <circle cx="12" cy="12" r="2" />
          <circle cx="19" cy="12" r="2" />
        </svg>
      </button>

      {open && (
        <div className="absolute right-0 top-full z-50 mt-1 w-48 rounded-lg border border-white/10 bg-deadly-surface p-1.5 shadow-xl">
          <ShareButton
            showId={showId}
            recordingId={recordingId}
            subtitle={shareSubtitle}
          >
            {(openShare) => (
              <button
                onClick={() => {
                  setOpen(false);
                  openShare();
                }}
                className={itemClass}
              >
                Share…
              </button>
            )}
          </ShareButton>

          {canPin && (
            <button
              onClick={() => {
                setOpen(false);
                onPinToggle?.();
              }}
              className={itemClass}
            >
              {pinned ? "Unpin" : "Pin to top"}
            </button>
          )}

          {remove && (
            <button
              onClick={() => {
                setOpen(false);
                setConfirmOpen(true);
              }}
              className="block w-full rounded-md px-2 py-1.5 text-left text-sm text-red-400 transition hover:bg-red-500/10"
            >
              {remove.label}
            </button>
          )}
        </div>
      )}

      {remove && (
        <ConfirmDialog
          open={confirmOpen}
          title={remove.confirmTitle}
          message={remove.confirmMessage}
          confirmLabel={remove.label}
          onCancel={() => setConfirmOpen(false)}
          onConfirm={() => {
            setConfirmOpen(false);
            remove.onConfirm();
          }}
        />
      )}
    </div>
  );
}
