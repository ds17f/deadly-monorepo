"use client";

const PLAY_STORE_URL =
  "https://play.google.com/store/apps/details?id=com.grateful.deadly";
// Self-hosted (vendored into /public) — hot-linking from play.google.com
// rendered intermittently (blockers / remote hiccups, no local fallback).
const GOOGLE_PLAY_BADGE_URL = "/google-play-badge.png";
const APP_STORE_URL = "https://apps.apple.com/us/app/thedeadly/id6753330346";
const APP_STORE_BADGE_URL =
  "https://developer.apple.com/assets/elements/badges/download-on-the-app-store.svg";

// Both badges render at the same height with auto width (official guidance is
// to align store badges by height), centered as a pair.
export default function GetTheApp() {
  return (
    <section className="mb-6">
      <h4 className="mb-3 text-sm font-bold uppercase tracking-wider text-deadly-title/80">
        Get the App
        <span className="ml-2 inline-block h-px w-16 align-middle bg-white/20" />
      </h4>
      <div className="flex items-center justify-center gap-3">
        <a href={APP_STORE_URL} target="_blank" rel="noopener noreferrer">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={APP_STORE_BADGE_URL}
            alt="Download on the App Store"
            className="h-10 w-auto"
          />
        </a>
        <a href={PLAY_STORE_URL} target="_blank" rel="noopener noreferrer">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={GOOGLE_PLAY_BADGE_URL}
            alt="Get it on Google Play"
            className="h-10 w-auto"
          />
        </a>
      </div>
    </section>
  );
}
