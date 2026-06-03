"use client";

/**
 * App-store badges, platform-aware. On a phone we show only the relevant store
 * (iOS → App Store, Android → Google Play); on desktop / unknown we show both.
 * Official guidance is to align badges by height, so both render `h-10 w-auto`.
 */

import { usePlatform } from "@/lib/usePlatform";

const PLAY_STORE_URL =
  "https://play.google.com/store/apps/details?id=com.grateful.deadly";
// Self-hosted (vendored into /public) — hot-linking from play.google.com
// rendered intermittently (blockers / remote hiccups, no local fallback).
const GOOGLE_PLAY_BADGE_URL = "/google-play-badge.png";
const APP_STORE_URL = "https://apps.apple.com/us/app/thedeadly/id6753330346";
const APP_STORE_BADGE_URL =
  "https://developer.apple.com/assets/elements/badges/download-on-the-app-store.svg";

function AppStoreBadge() {
  return (
    <a href={APP_STORE_URL} target="_blank" rel="noopener noreferrer">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={APP_STORE_BADGE_URL} alt="Download on the App Store" className="h-10 w-auto" />
    </a>
  );
}

function GooglePlayBadge() {
  return (
    <a href={PLAY_STORE_URL} target="_blank" rel="noopener noreferrer">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={GOOGLE_PLAY_BADGE_URL} alt="Get it on Google Play" className="h-10 w-auto" />
    </a>
  );
}

export default function StoreBadges({ className }: { className?: string }) {
  const platform = usePlatform();
  return (
    <div className={`flex items-center justify-center gap-3 ${className ?? ""}`}>
      {platform !== "android" && <AppStoreBadge />}
      {platform !== "ios" && <GooglePlayBadge />}
    </div>
  );
}
