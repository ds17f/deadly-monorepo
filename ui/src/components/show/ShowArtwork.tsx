"use client";

import { useState } from "react";

// Square show "ticket" artwork: prefer the catalog image (ticket stub /
// photo), otherwise fall back to the Deadly logo. We deliberately do NOT use
// the Archive.org item thumbnail — it returns a generic grey placeholder with
// HTTP 200 for art-less recordings, which would mask the logo fallback. If the
// catalog image link is dead, the <img> error walks us to the logo.
//
// `bestRecordingId` is accepted (callers pass it) but intentionally unused.
export default function ShowArtwork({
  image,
  alt = "",
}: {
  image?: string | null;
  bestRecordingId?: string | null;
  alt?: string;
}) {
  const sources = [image].filter(Boolean) as string[];

  const [idx, setIdx] = useState(0);
  const showingFallback = idx >= sources.length;
  const src = showingFallback ? "/cover-fallback.png" : sources[idx];

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt={alt}
      width={56}
      height={56}
      loading="lazy"
      referrerPolicy="no-referrer"
      onError={() => {
        if (idx < sources.length) setIdx((i) => i + 1);
      }}
      className="h-14 w-14 flex-shrink-0 rounded-md bg-white/5 object-cover"
    />
  );
}
