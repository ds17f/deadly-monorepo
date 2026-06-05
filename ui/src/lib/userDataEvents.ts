"use client";

import { useEffect } from "react";

// Lightweight client-side reactivity for the /me library surfaces. The web has
// no reactive DB like mobile, so list views re-fetch when user-data changes in
// this session (e.g. a play records a recent) or when the tab regains focus /
// is restored from bfcache. The planned upgrade is WS push (see the
// `project-userdata-realtime-deferred` memory) — this is the focus/event
// stand-in.
export const USERDATA_CHANGED = "deadly:userdata-changed";

export function notifyUserDataChanged(): void {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(USERDATA_CHANGED));
  }
}

// Re-run `load` on a userdata-changed event, on focus, and on bfcache restore.
// Keep `load` stable (useCallback) so the listeners aren't re-bound each render.
export function useUserDataRefresh(load: () => void): void {
  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === "visible") load();
    };
    window.addEventListener(USERDATA_CHANGED, load);
    window.addEventListener("focus", onVisible);
    window.addEventListener("pageshow", load);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      window.removeEventListener(USERDATA_CHANGED, load);
      window.removeEventListener("focus", onVisible);
      window.removeEventListener("pageshow", load);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [load]);
}
