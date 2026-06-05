"use client";

/**
 * Show-page "Get the App" call-to-action for the top of the liner-notes rail
 * (above Key highlights), mirroring the home right rail. Store badges only —
 * the QR to open this show on a phone already lives lower in the rail
 * (ShowLinerNotes "Open in App").
 */

import GetTheApp from "@/components/home/GetTheApp";

export default function ShowAppCta() {
  return (
    <section className="rounded-lg bg-deadly-surface p-4">
      <GetTheApp />
    </section>
  );
}
