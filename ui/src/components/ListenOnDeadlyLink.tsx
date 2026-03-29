"use client";

import Image from "next/image";
import { getShareBaseUrl } from "@/lib/share";

export default function ListenOnDeadlyLink({
  artistId,
  showId,
  recordingId,
}: {
  artistId: string;
  showId: string;
  recordingId: string | null;
}) {
  const base = getShareBaseUrl();
  const href = recordingId
    ? `${base}/${artistId}/${showId}/${recordingId}`
    : `${base}/${artistId}/${showId}`;

  return (
    <a
      href={href}
      className="inline-flex w-full items-center justify-center gap-2 rounded-lg border border-deadly-accent px-5 py-3 font-semibold text-white transition-colors hover:bg-deadly-accent/10 lg:hidden"
    >
      <Image src="/logo.png" alt="" width={24} height={24} />
      Listen on Deadly
    </a>
  );
}
