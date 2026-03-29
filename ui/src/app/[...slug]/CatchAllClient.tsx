"use client";

import { usePathname } from "next/navigation";
import ArtistDetailContent from "@/app/artists/ArtistDetailContent";
import DynamicShowPage from "@/components/DynamicShowPage";

export default function CatchAllClient() {
  const pathname = usePathname();
  const segments = pathname.split("/").filter(Boolean);

  if (segments.length === 0 || segments[0] === "_") return null;

  const artistId = decodeURIComponent(segments[0]);

  if (segments.length === 1) {
    return <ArtistDetailContent artistId={artistId} />;
  }

  // 2 segments: /{artist}/{showId}
  // 3 segments: /{artist}/{showId}/{recordingId}
  const showId = decodeURIComponent(segments[1]);
  const selectedRecordingId = segments[2] ? decodeURIComponent(segments[2]) : undefined;

  return <DynamicShowPage showId={showId} selectedRecordingId={selectedRecordingId} />;
}
