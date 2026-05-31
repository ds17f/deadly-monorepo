"use client";

import { useState } from "react";

// Square show "ticket" artwork, mirroring the mobile ShowArtwork: prefer the
// catalog image (ticket stub / photo), fall back to the Archive.org item
// thumbnail for the best recording, then the Deadly logo. We walk the chain
// on <img> error so a dead CDN link or missing Archive thumbnail degrades
// gracefully instead of showing a broken image.
export default function ShowArtwork({
  image,
  bestRecordingId,
  alt = "",
}: {
  image?: string | null;
  bestRecordingId?: string | null;
  alt?: string;
}) {
  const sources = [
    image,
    bestRecordingId
      ? `https://archive.org/services/img/${bestRecordingId}`
      : null,
  ].filter(Boolean) as string[];

  const [idx, setIdx] = useState(0);
  const src = idx < sources.length ? sources[idx] : "/logo.png";

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
