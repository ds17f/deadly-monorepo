"use client";

import { useEffect, useState } from "react";
import { syncVersionLabel } from "@/lib/syncVersions";

const DISMISS_KEY = "sync-version-banner-dismissed";

// Persistent banner across the whole /me section. During the app-store
// rollout window every existing app user is on a build older than the sync
// floor, so their data hasn't been pushed and the profile reads as empty.
// This explains the gap everywhere — not just on an empty list — and can be
// dismissed once the user gets it.
export default function SyncVersionBanner() {
  const [dismissed, setDismissed] = useState(true);

  useEffect(() => {
    setDismissed(localStorage.getItem(DISMISS_KEY) === "1");
  }, []);

  if (dismissed) return null;

  const dismiss = () => {
    localStorage.setItem(DISMISS_KEY, "1");
    setDismissed(true);
  };

  return (
    <div className="mb-6 flex items-start gap-3 rounded-lg border border-deadly-accent/30 bg-deadly-accent/5 p-3">
      <svg
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        className="mt-0.5 flex-shrink-0 text-deadly-accent"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="10" />
        <line x1="12" y1="8" x2="12" y2="12" />
        <line x1="12" y1="16" x2="12.01" y2="16" />
      </svg>
      <p className="min-w-0 flex-1 text-sm text-white/70">
        Your favorites, recent plays, and reviews sync from the Deadly app.
        Syncing requires the latest version ({syncVersionLabel()}) — update from
        the App Store or Google Play and your data will appear here.
      </p>
      <button
        type="button"
        onClick={dismiss}
        aria-label="Dismiss"
        className="-m-1 flex-shrink-0 rounded p-1 text-white/40 transition hover:text-white"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      </button>
    </div>
  );
}
