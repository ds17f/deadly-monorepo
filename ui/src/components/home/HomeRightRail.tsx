"use client";

/**
 * The home right rail — conversion + context. "Get the app" (store badges
 * always; a QR to open this site on a phone, desktop-only) plus a Now Playing
 * card when something is active. On mobile this rail leads above the content.
 */

import Link from "next/link";
import { QRCodeSVG } from "qrcode.react";
import { usePlayer } from "@/contexts/PlayerContext";
import GetTheApp from "./GetTheApp";

function NowPlaying() {
  const { activeShow, status, tracks, currentTrackIndex } = usePlayer();
  if (!activeShow || status === "idle") return null;
  const track =
    tracks && currentTrackIndex >= 0 ? tracks[currentTrackIndex] : null;
  const img = activeShow.image || "/logo.png";
  const isLogo = img === "/logo.png";
  return (
    <section className="mb-6">
      <h4 className="mb-2 text-sm font-bold uppercase tracking-wider text-deadly-title/80">
        Now Playing
      </h4>
      <Link
        href={`/shows/${activeShow.showId}`}
        className="flex items-center gap-3 rounded-lg p-2 transition hover:bg-white/10"
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={img}
          alt=""
          referrerPolicy="no-referrer"
          className={`h-14 w-14 flex-shrink-0 rounded-md bg-white/5 ${
            isLogo ? "object-contain p-1.5" : "object-cover"
          }`}
        />
        <span className="min-w-0 flex-1">
          {track && (
            <span className="block truncate text-sm font-semibold text-white">
              {track.title}
            </span>
          )}
          <span className="block truncate text-xs text-white/60">
            {activeShow.date}
          </span>
          <span className="block truncate text-xs text-white/40">
            {activeShow.venue}
          </span>
        </span>
      </Link>
    </section>
  );
}

export default function HomeRightRail() {
  const origin =
    typeof window !== "undefined" ? window.location.origin : "https://thedeadly.app";
  return (
    <div className="rounded-lg bg-deadly-surface p-4 lg:min-h-full">
      <NowPlaying />

      <GetTheApp />

      <div className="hidden lg:block">
        <p className="mb-2 text-xs text-white/50">Scan to open on your phone</p>
        <div className="inline-block rounded-lg bg-white p-3">
          <QRCodeSVG value={origin} size={120} level="M" bgColor="#ffffff" fgColor="#121212" />
        </div>
      </div>
    </div>
  );
}
