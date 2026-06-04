"use client";

import { useCallback, useEffect, useState } from "react";

// A dismissible "add to home screen" banner. Android/Chromium gets a real
// Install button (via the beforeinstallprompt event); iOS Safari — which has no
// install API — gets a hint pointing at the Share → Add to Home Screen flow.
// Shown only on mobile web when NOT already installed. Dismissal is permanent
// (localStorage, no expiry) per the "dismissable, maybe even forever" ask, and
// actually installing dismisses it for good too.

const DISMISS_KEY = "deadly:install-prompt-dismissed";

// The beforeinstallprompt event isn't in the standard lib DOM types.
type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
};

function alreadyDismissed(): boolean {
  try {
    return localStorage.getItem(DISMISS_KEY) === "1";
  } catch {
    return false;
  }
}

function isStandalone(): boolean {
  return (
    window.matchMedia?.("(display-mode: standalone)").matches ||
    // iOS Safari exposes navigator.standalone when launched from the home screen
    (navigator as unknown as { standalone?: boolean }).standalone === true
  );
}

export default function InstallPrompt() {
  const [mode, setMode] = useState<"none" | "android" | "ios">("none");
  const [deferred, setDeferred] = useState<BeforeInstallPromptEvent | null>(null);

  const dismissForever = useCallback(() => {
    try {
      localStorage.setItem(DISMISS_KEY, "1");
    } catch {
      // ignore storage failures — worst case the banner returns next visit
    }
    setMode("none");
  }, []);

  useEffect(() => {
    if (alreadyDismissed() || isStandalone()) return;

    // Android / Chromium — a real install is available.
    const onBeforeInstallPrompt = (e: Event) => {
      e.preventDefault(); // suppress Chrome's mini-infobar; we drive our own UI
      setDeferred(e as BeforeInstallPromptEvent);
      setMode("android");
    };
    window.addEventListener("beforeinstallprompt", onBeforeInstallPrompt);

    // Once installed, never show it again.
    const onInstalled = () => dismissForever();
    window.addEventListener("appinstalled", onInstalled);

    // iOS Safari has no beforeinstallprompt — detect it and show the hint.
    // Exclude Chrome/Firefox/Edge-on-iOS (CriOS/FxiOS/EdgiOS) and in-app
    // webviews, where Add-to-Home-Screen isn't available.
    const ua = navigator.userAgent;
    const isIOS =
      /iphone|ipad|ipod/i.test(ua) ||
      // iPadOS reports as Mac with touch
      (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
    const isIOSSafari = isIOS && /safari/i.test(ua) && !/crios|fxios|edgios|opios/i.test(ua);
    if (isIOSSafari) setMode("ios");

    return () => {
      window.removeEventListener("beforeinstallprompt", onBeforeInstallPrompt);
      window.removeEventListener("appinstalled", onInstalled);
    };
  }, [dismissForever]);

  const install = useCallback(async () => {
    if (!deferred) return;
    await deferred.prompt();
    await deferred.userChoice.catch(() => undefined);
    // Whether accepted or not, don't nag again on this device.
    dismissForever();
  }, [deferred, dismissForever]);

  if (mode === "none") return null;

  return (
    <div
      className="fixed inset-x-0 bottom-0 z-[60] px-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-3 lg:hidden"
      role="dialog"
      aria-label="Install The Deadly"
    >
      <div className="mx-auto flex max-w-md items-center gap-3 rounded-xl border border-white/10 bg-deadly-surface/95 p-3 shadow-lg backdrop-blur">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src="/icon-192.png"
          alt=""
          className="h-11 w-11 flex-shrink-0 rounded-[22%]"
        />
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-white">Install The Deadly</p>
          {mode === "android" ? (
            <p className="text-xs text-white/60">Add it to your home screen.</p>
          ) : (
            <p className="text-xs text-white/60">
              Tap{" "}
              {/* iOS Safari share glyph: box with an up arrow */}
              <svg
                width="13"
                height="13"
                viewBox="0 0 24 24"
                fill="none"
                aria-hidden
                className="inline-block translate-y-[1px] text-white/80"
              >
                <path
                  d="M12 3v12M12 3L8 7M12 3l4 4M6 11v8a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1v-8"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>{" "}
              <span className="font-medium text-white/80">Share</span>, then{" "}
              <span className="font-medium text-white/80">
                Add to Home Screen
              </span>
              .
            </p>
          )}
        </div>

        {mode === "android" && (
          <button
            onClick={install}
            className="flex-shrink-0 rounded-md bg-white px-3 py-1.5 text-sm font-semibold text-black transition hover:opacity-90"
          >
            Install
          </button>
        )}
        <button
          onClick={dismissForever}
          aria-label="Dismiss"
          className="flex-shrink-0 rounded-md p-1.5 text-white/50 transition hover:bg-white/10 hover:text-white"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
            <path
              d="M6 6l12 12M18 6L6 18"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
            />
          </svg>
        </button>
      </div>
    </div>
  );
}
