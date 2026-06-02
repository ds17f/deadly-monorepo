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
  const img = activeShow.image || "/cover-fallback.png";
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
          className="h-14 w-14 flex-shrink-0 rounded-md bg-white/5 object-cover"
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

// Our mission — the app's own words (Settings → Our Mission), condensed for a
// narrow column. Tells first-time visitors what this is and why it exists.
function Mission() {
  return (
    <section className="mt-6 border-t border-white/10 pt-5">
      <h4 className="mb-3 text-sm font-bold uppercase tracking-wider text-deadly-title/80">
        Our Mission
      </h4>
      <div className="space-y-3 text-sm leading-relaxed text-white/60">
        <p>
          We built this for one reason: to make it easy to explore, enjoy, and
          share live music from the Internet Archive&apos;s vast collection of
          concert recordings — as easy as a modern streaming experience.
        </p>
        <p>
          We have deep respect for the long-standing tradition of taping and
          sharing live music freely, non-commercially, and in community.
        </p>
        <p>
          The app is completely{" "}
          <a
            href="https://github.com/ds17f/deadly-monorepo"
            target="_blank"
            rel="noopener noreferrer"
            className="text-deadly-heading hover:text-white"
          >
            open source
          </a>
          , and no money is made from streaming. It exists because one fan
          wanted a modern way to listen to the music he loves.
        </p>
      </div>
      <a
        href="https://archive.org/donate/"
        target="_blank"
        rel="noopener noreferrer"
        className="mt-4 inline-flex items-center gap-2 rounded-full border border-white/15 px-4 py-1.5 text-sm font-medium text-white/80 transition hover:border-white/30 hover:text-white"
      >
        <span className="text-deadly-accent">♥</span>
        Donate to the Internet Archive
      </a>
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

      <div className="hidden flex-col items-center lg:flex">
        <p className="mb-2 text-xs text-white/50">Scan to open on your phone</p>
        <div className="rounded-lg bg-white p-3">
          <QRCodeSVG value={origin} size={120} level="M" bgColor="#ffffff" fgColor="#121212" />
        </div>
      </div>

      <Mission />
    </div>
  );
}
