"use client";

import StoreBadges from "./StoreBadges";

// Store badges, platform-aware (only the relevant store on a phone). See
// StoreBadges for the badge URLs / sizing.
export default function GetTheApp() {
  return (
    <section className="mb-6">
      <h4 className="mb-3 text-sm font-bold uppercase tracking-wider text-deadly-title/80">
        Get the App
        <span className="ml-2 inline-block h-px w-16 align-middle bg-white/20" />
      </h4>
      <StoreBadges />
    </section>
  );
}
