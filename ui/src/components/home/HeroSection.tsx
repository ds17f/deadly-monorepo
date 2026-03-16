"use client";

import Image from "next/image";
import AppStoreBadge from "@/components/AppStoreBadge";

const PLAY_STORE_URL =
  "https://play.google.com/store/apps/details?id=com.grateful.deadly";
const GOOGLE_PLAY_BADGE_URL =
  "https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png";

export default function HeroSection({ totalShows }: { totalShows: number }) {
  return (
    <section className="relative mb-12 overflow-hidden rounded-2xl py-12 text-center">
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_rgba(220,20,60,0.15)_0%,_transparent_70%)]" />
      <div className="relative">
        <Image
          src="/logo.png"
          alt="The Deadly logo"
          width={80}
          height={80}
          className="mx-auto mb-4"
        />
        <h1 className="text-4xl font-bold text-white md:text-5xl">
          The Deadly
        </h1>
        <p className="mt-3 text-lg text-white/60">
          Every Grateful Dead concert. Free. Forever.
        </p>
        <p className="mx-auto mt-4 max-w-xl text-sm leading-relaxed text-white/40">
          The Deadly is an open-source app that brings all{" "}
          {totalShows.toLocaleString()} known Grateful Dead shows into one
          place — sourced from the Internet Archive, enriched with AI-generated
          reviews, setlists, and ratings. Browse three decades of live music
          from 1965 to 1995.
        </p>
        <div className="mt-6 flex items-center justify-center gap-3">
          <a href={PLAY_STORE_URL} target="_blank" rel="noopener noreferrer">
            <Image
              src={GOOGLE_PLAY_BADGE_URL}
              alt="Get it on Google Play"
              width={160}
              height={48}
              unoptimized
            />
          </a>
          <AppStoreBadge width={160} height={48} />
        </div>
      </div>
    </section>
  );
}
