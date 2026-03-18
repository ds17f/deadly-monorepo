"use client";

import { QRCodeSVG } from "qrcode.react";
import { getShareBaseUrl } from "@/lib/share";

export default function ShowQRCode({
  showId,
  recordingId,
}: {
  showId: string;
  recordingId?: string;
}) {
  const base = getShareBaseUrl();
  const url = recordingId
    ? `${base}/shows/${showId}/recording/${recordingId}`
    : `${base}/shows/${showId}`;

  return (
    <div className="mt-4 rounded-lg border border-white/10 bg-deadly-surface p-4">
      <h4 className="mb-3 text-sm font-bold text-deadly-title">
        Open in App
      </h4>
      <div className="flex justify-center">
        <div className="relative inline-block rounded-lg bg-white p-3">
          <QRCodeSVG
            value={url}
            size={160}
            level="H"
            bgColor="#ffffff"
            fgColor="#121212"
          />
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="flex h-[35px] w-[35px] items-center justify-center rounded-full bg-white">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src="/logo.png" alt="" width={28} height={28} />
            </div>
          </div>
        </div>
      </div>
      <a
        href={url}
        target="_blank"
        rel="noopener noreferrer"
        className="mt-2 block text-center text-xs text-white/40 hover:text-white/60 break-all"
      >
        {url}
      </a>
    </div>
  );
}
