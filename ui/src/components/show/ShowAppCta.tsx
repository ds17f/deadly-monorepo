"use client";

/**
 * Show-page "Get the App" call-to-action for the top of the liner-notes rail
 * (above Key highlights), mirroring the home right rail. Store badges plus a
 * compact QR that opens THIS show on a phone (desktop only — pointless on a
 * phone you're already holding).
 */

import { QRCodeSVG } from "qrcode.react";
import { getShareBaseUrl } from "@/lib/share";
import GetTheApp from "@/components/home/GetTheApp";

export default function ShowAppCta({
  showId,
  recordingId,
}: {
  showId: string;
  recordingId?: string | null;
}) {
  const base = getShareBaseUrl();
  const url = recordingId
    ? `${base}/shows/${showId}/recording/${recordingId}`
    : `${base}/shows/${showId}`;
  return (
    <section className="rounded-lg bg-deadly-surface p-4">
      <GetTheApp />
      <div className="hidden flex-col items-center lg:flex">
        <p className="mb-2 text-xs text-white/50">
          Scan to open this show on your phone
        </p>
        <div className="rounded-lg bg-white p-2.5">
          <QRCodeSVG value={url} size={92} level="M" bgColor="#ffffff" fgColor="#121212" />
        </div>
      </div>
    </section>
  );
}
