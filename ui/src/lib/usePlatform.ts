"use client";

import { useEffect, useState } from "react";

export type Platform = "ios" | "android" | "other";

/**
 * Client-side mobile-platform detection. Returns `null` until mounted (so the
 * server and first client render agree — no hydration mismatch), then "ios" /
 * "android" on phones, "other" on desktop/unknown. Used to show only the
 * relevant app-store badge to phone visitors.
 */
export function usePlatform(): Platform | null {
  const [platform, setPlatform] = useState<Platform | null>(null);
  useEffect(() => {
    const ua = navigator.userAgent || "";
    if (/iPhone|iPad|iPod/i.test(ua)) setPlatform("ios");
    else if (/Android/i.test(ua)) setPlatform("android");
    else setPlatform("other");
  }, []);
  return platform;
}
