"use client";

import Image from "next/image";
import AppStoreBadge from "@/components/AppStoreBadge";

const PLAY_STORE_URL =
  "https://play.google.com/store/apps/details?id=com.grateful.deadly";
const GOOGLE_PLAY_BADGE_URL =
  "https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png";

export default function GetTheApp() {
  return (
    <section className="mb-6">
      <h4 className="mb-3 text-sm font-bold uppercase tracking-wider text-deadly-title/80">
        Get the App
        <span className="ml-2 inline-block h-px w-16 align-middle bg-white/20" />
      </h4>
      <div className="flex items-center gap-3">
        <a href={PLAY_STORE_URL} target="_blank" rel="noopener noreferrer">
          <Image
            src={GOOGLE_PLAY_BADGE_URL}
            alt="Get it on Google Play"
            width={140}
            height={42}
            unoptimized
          />
        </a>
        <AppStoreBadge />
      </div>
    </section>
  );
}
