"use client";

import { usePathname, redirect } from "next/navigation";

export default function ShowRedirectClient() {
  const pathname = usePathname();
  // pathname is /show/{id}/... — extract the first segment after /show/
  const id = pathname.split("/").filter(Boolean)[1] ?? "";
  redirect(`/shows/${id}`);
  return null;
}
