// Minimal service worker. Its only job is to satisfy Android Chrome's
// installability criteria (a registered SW with a fetch handler), which unlocks
// the "Install app" prompt. It intentionally does NOT cache anything — this is
// a streaming app, offline has little value, and precaching app-shell assets
// would invite stale-asset bugs across deploys. Requests pass straight through
// to the network. iOS 13 doesn't need this for Add-to-Home-Screen.
self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

// Pass-through. Present so the SW counts as a fetch handler; no caching.
self.addEventListener("fetch", () => {});
