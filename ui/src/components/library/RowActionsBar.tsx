"use client";

import { useState } from "react";
import QrButton from "@/components/share/QrButton";
import { useShareLink } from "@/components/share/useShareLink";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import type { RemoveConfig } from "./RowActionsMenu";

// Inline icon-button row of the same actions as RowActionsMenu — used in list
// view, where the wide rows have room for discrete QR / Share / Pin / Remove
// icons instead of a hidden "⋯" overflow menu. QR opens the scan code; Share
// copies the link and toasts.
export default function RowActionsBar({
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
  const [confirmOpen, setConfirmOpen] = useState(false);
  const shareLink = useShareLink();

  const btn =
    "flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full text-white/50 transition hover:bg-white/10 hover:text-white";

  return (
    <div className="flex items-center gap-1 pr-1">
      <QrButton showId={showId} recordingId={recordingId} subtitle={shareSubtitle}>
        {(openQr) => (
          <button onClick={openQr} aria-label="QR code" title="QR code" className={btn}>
            <svg
              width="19"
              height="19"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <rect x="3" y="3" width="7" height="7" rx="1" />
              <rect x="14" y="3" width="7" height="7" rx="1" />
              <rect x="3" y="14" width="7" height="7" rx="1" />
              <path d="M14 14h3v3M21 21v.01M21 14v3M14 21h3" />
            </svg>
          </button>
        )}
      </QrButton>

      <button
        onClick={() => shareLink(showId, recordingId)}
        aria-label="Copy link"
        title="Copy link"
        className={btn}
      >
        <svg
          width="19"
          height="19"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M12 3v12M8 7l4-4 4 4M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6" />
        </svg>
      </button>

      {canPin && (
        <button
          onClick={onPinToggle}
          aria-label={pinned ? "Unpin" : "Pin to top"}
          title={pinned ? "Unpin" : "Pin to top"}
          className={`${btn} ${pinned ? "text-deadly-accent hover:text-deadly-accent" : ""}`}
        >
          <svg width="19" height="19" viewBox="0 0 24 24" fill="currentColor">
            <path d="M16 3v2l-1 1v5l3 3v2h-5v6h-2v-6H4v-2l3-3V6L6 5V3z" />
          </svg>
        </button>
      )}

      {remove && (
        <button
          onClick={() => setConfirmOpen(true)}
          aria-label={remove.label}
          title={remove.label}
          className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full text-white/50 transition hover:bg-red-500/10 hover:text-red-400"
        >
          <svg
            width="19"
            height="19"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14M10 11v6M14 11v6" />
          </svg>
        </button>
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
