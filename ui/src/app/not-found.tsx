"use client";

import { useEffect, useState } from "react";
import Image from "next/image";

const PLAY_STORE_URL =
  "https://play.google.com/store/apps/details?id=com.grateful.deadly";
const GOOGLE_PLAY_BADGE_URL =
  "https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png";

export default function NotFound() {
  const [deepLink, setDeepLink] = useState("deadly://");
  const [archiveUrl, setArchiveUrl] = useState<string | null>(null);

  useEffect(() => {
    const segments = window.location.pathname.split("/").filter(Boolean);

    // Build deep link from path: deadly://show/{id}[/recording/{rid}]
    if (segments.length > 0) {
      setDeepLink("deadly:/" + window.location.pathname);
    }

    // Detect recording ID for Archive.org link
    if (
      segments[0] === "show" &&
      segments[2] === "recording" &&
      segments[3]
    ) {
      const recordingId = decodeURIComponent(segments[3]);
      setArchiveUrl(
        "https://archive.org/details/" + encodeURIComponent(recordingId)
      );
    }
  }, []);

  return (
    <div className="flex flex-col items-center py-16 text-center">
      <h1 className="mb-2 text-3xl font-bold">The Deadly</h1>
      <p className="mb-2 text-white/50">
        Listen to every Grateful Dead show, free.
      </p>
      <p className="mb-8 max-w-md text-sm leading-relaxed text-white/40">
        The Deadly is a free, open-source app that streams every Grateful Dead
        concert from the{" "}
        <a
          href="https://archive.org/details/GratefulDead"
          target="_blank"
          rel="noopener noreferrer"
          className="text-white/50 hover:text-white/70"
        >
          Internet Archive
        </a>
        . No account. No sign-up. No ads.
      </p>

      <a
        href={deepLink}
        className="mb-4 block rounded-lg bg-deadly-red px-7 py-3.5 text-lg font-semibold text-white hover:bg-red-800"
      >
        Open in The Deadly App
      </a>

      {archiveUrl && (
        <a
          href={archiveUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="mb-8 block rounded-lg border border-white/20 px-7 py-3 text-white/60 hover:border-white/50 hover:text-white"
        >
          Listen on Archive.org
        </a>
      )}

      <div className="flex items-center gap-3">
        <a href={PLAY_STORE_URL} target="_blank" rel="noopener noreferrer">
          <Image
            src={GOOGLE_PLAY_BADGE_URL}
            alt="Get it on Google Play"
            width={140}
            height={42}
            unoptimized
          />
        </a>
      </div>

      <p className="mt-8 text-sm text-white/30">
        We do not collect, store, or sell any personal data.
      </p>
    </div>
  );
}
