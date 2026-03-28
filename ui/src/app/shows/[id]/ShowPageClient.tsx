"use client";

import { usePathname } from "next/navigation";
import DynamicShowPage from "@/components/DynamicShowPage";

export default function ShowPageClient() {
  const pathname = usePathname();
  // pathname is /shows/{id} or /shows/{id}/ — extract the ID segment
  const id = pathname.split("/").filter(Boolean)[1] ?? "";
  return <DynamicShowPage showId={id} />;
}
