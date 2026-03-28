"use client";

import { usePathname } from "next/navigation";
import ArtistDetailContent from "../ArtistDetailContent";

export default function ArtistPageClient() {
  const pathname = usePathname();
  const id = pathname.split("/").filter(Boolean)[1] ?? "";
  if (!id || id === "_") return null;
  return <ArtistDetailContent artistId={id} />;
}
