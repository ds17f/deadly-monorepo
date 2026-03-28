"use client";

import { useSearchParams, redirect } from "next/navigation";
import { Suspense } from "react";

function ArtistRedirect() {
  const params = useSearchParams();
  const id = params.get("id");
  if (id) {
    redirect(`/artists/${id}`);
    return null;
  }
  // No id param — redirect to home
  redirect("/");
  return null;
}

export default function ArtistPage() {
  return (
    <Suspense>
      <ArtistRedirect />
    </Suspense>
  );
}
