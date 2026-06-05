"use client";

/**
 * A contextual back chevron for the mobile top bar — shown ONLY when:
 *   1. the app is running as an installed PWA (display-mode: standalone, or
 *      iOS's navigator.standalone), where there's no browser back button and
 *      iOS gives no edge-swipe-back; and
 *   2. we're on a detail/leaf route (not a primary tab — Home or the /me
 *      Library/Settings area, which the tab bar already reaches).
 *
 * Browser-tab users keep their real back button, so we render nothing for them
 * to avoid redundant chrome. `lg:hidden` — desktop has the rail + a real
 * window. Mounted-gated so SSR/first paint never shows it (avoids a flash and
 * a hydration mismatch on the media query).
 */

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";

// Primary tab destinations + their sub-areas — no "back" needed here.
function isPrimaryRoute(path: string): boolean {
  return path === "/" || path.startsWith("/me");
}

export default function MobileBackButton() {
  const pathname = usePathname();
  const router = useRouter();
  const [standalone, setStandalone] = useState(false);

  useEffect(() => {
    const mql = window.matchMedia("(display-mode: standalone)");
    const update = () =>
      setStandalone(
        mql.matches ||
          // iOS Safari predates display-mode; it exposes this instead.
          (window.navigator as unknown as { standalone?: boolean }).standalone === true,
      );
    update();
    mql.addEventListener("change", update);
    return () => mql.removeEventListener("change", update);
  }, []);

  if (!standalone || isPrimaryRoute(pathname)) return null;

  const goBack = () => {
    // A fresh launch into a deep link has no in-app history to pop — going
    // "back" there would try to exit the app. Fall back to Home instead.
    if (window.history.length > 1) router.back();
    else router.push("/");
  };

  return (
    <button
      onClick={goBack}
      aria-label="Back"
      className="-ml-1 mr-1 flex flex-shrink-0 items-center justify-center rounded-full p-1 text-white/70 transition-colors hover:text-white lg:hidden"
    >
      <svg width="26" height="26" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
        <path d="M15.4 7.4 14 6l-6 6 6 6 1.4-1.4-4.6-4.6z" />
      </svg>
    </button>
  );
}
