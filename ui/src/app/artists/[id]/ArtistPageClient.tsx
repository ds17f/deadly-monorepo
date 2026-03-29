"use client";

import { usePathname, redirect } from "next/navigation";

export default function ArtistPageClient() {
  const pathname = usePathname();
  const id = pathname.split("/").filter(Boolean)[1] ?? "";
  if (!id || id === "_") return null;
  redirect(`/${id}`);
  return null;
}
