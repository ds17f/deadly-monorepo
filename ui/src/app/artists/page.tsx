"use client";

import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import ArtistDetailContent from "./ArtistDetailContent";

function ArtistPageInner() {
  const params = useSearchParams();
  const id = params.get("id");
  if (!id) return null;
  return <ArtistDetailContent artistId={id} />;
}

export default function ArtistPage() {
  return (
    <Suspense>
      <ArtistPageInner />
    </Suspense>
  );
}
