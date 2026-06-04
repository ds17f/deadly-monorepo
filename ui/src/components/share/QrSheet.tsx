"use client";

import { useEffect, useState } from "react";
import ShowQRCode from "@/components/ShowQRCode";
import { buildShareUrl, copyToClipboard } from "@/lib/share";

/**
 * Scan-to-phone QR modal for a show: the QR code (the desktop→phone path) plus
 * a copy-link fallback. Plain link sharing is handled inline by the Share
 * buttons via useShareLink — this sheet is specifically the scannable code.
 */
export default function QrSheet({
  open,
  onClose,
  showId,
  recordingId,
  title = "Scan to open",
  subtitle,
}: {
  open: boolean;
  onClose: () => void;
  showId: string;
  recordingId?: string | null;
  title?: string;
  subtitle?: string;
}) {
  const [copied, setCopied] = useState(false);
  const url = buildShareUrl(showId, recordingId);

  useEffect(() => {
    if (!open) return;
    setCopied(false);
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    // Capture phase so the close fires before any other Escape handler can
    // stop the event from reaching us.
    document.addEventListener("keydown", onKey, true);
    return () => document.removeEventListener("keydown", onKey, true);
  }, [open, onClose]);

  if (!open) return null;

  async function copy() {
    // On failure (clipboard blocked) the QR + visible URL still give the link.
    if (await copyToClipboard(url)) {
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    }
  }

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-4"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-sm rounded-xl border border-white/10 bg-deadly-surface p-5 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="text-base font-bold text-white">{title}</h2>
            {subtitle && (
              <p className="truncate text-sm text-white/50">{subtitle}</p>
            )}
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            className="-mr-1 -mt-1 flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full text-white/50 transition hover:bg-white/10 hover:text-white"
          >
            ✕
          </button>
        </div>

        <ShowQRCode showId={showId} recordingId={recordingId ?? undefined} />

        <button
          onClick={copy}
          className="mt-4 w-full rounded-lg bg-white/10 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-white/20"
        >
          {copied ? "Copied!" : "Copy link"}
        </button>
      </div>
    </div>
  );
}
