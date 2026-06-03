"use client";

import { useEffect, useState } from "react";
import ShowQRCode from "@/components/ShowQRCode";
import { getShareBaseUrl } from "@/lib/share";

/**
 * Share modal for a show: scan-to-phone QR, copy link, and the native share
 * sheet where the browser supports it (navigator.share — great on mobile, the
 * QR is the desktop→phone path). Reuses ShowQRCode and getShareBaseUrl.
 */
export default function ShareSheet({
  open,
  onClose,
  showId,
  recordingId,
  title = "Share show",
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
  const [canNativeShare, setCanNativeShare] = useState(false);

  const url = recordingId
    ? `${getShareBaseUrl()}/shows/${showId}/recording/${recordingId}`
    : `${getShareBaseUrl()}/shows/${showId}`;

  useEffect(() => {
    setCanNativeShare(typeof navigator !== "undefined" && !!navigator.share);
  }, []);

  useEffect(() => {
    if (!open) return;
    setCopied(false);
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  async function copy() {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      // Clipboard blocked (insecure context / permissions) — the QR + visible
      // URL under it still give the user the link.
    }
  }

  async function nativeShare() {
    try {
      await navigator.share({ title: subtitle ?? "The Deadly", url });
    } catch {
      // User cancelled or share failed — no-op.
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

        <div className="mt-4 flex gap-2">
          <button
            onClick={copy}
            className="flex-1 rounded-lg bg-white/10 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-white/20"
          >
            {copied ? "Copied!" : "Copy link"}
          </button>
          {canNativeShare && (
            <button
              onClick={nativeShare}
              className="flex-1 rounded-lg bg-deadly-accent px-4 py-2.5 text-sm font-bold text-white transition hover:opacity-90"
            >
              Share…
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
