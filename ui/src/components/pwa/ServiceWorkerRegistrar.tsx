"use client";

import { useEffect } from "react";

// Registers the minimal service worker (public/sw.js) so Android Chrome offers
// "Install app". Renders nothing. Safe no-op where service workers are
// unsupported (e.g. iOS 13 Add-to-Home-Screen doesn't need it).
export default function ServiceWorkerRegistrar() {
  useEffect(() => {
    if (!("serviceWorker" in navigator)) return;
    navigator.serviceWorker.register("/sw.js").catch(() => {
      // Registration failure is non-fatal — the site works without it; we just
      // forgo the installability prompt.
    });
  }, []);

  return null;
}
