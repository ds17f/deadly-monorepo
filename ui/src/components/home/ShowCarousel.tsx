"use client";

/**
 * A horizontal, cover-art carousel of shows — the mobile-app home unit in the
 * wide middle column. Spotify-style: the native scrollbar is hidden, the row
 * scrolls with a horizontal wheel/trackpad, and fade-in ‹ › buttons (shown on
 * hover, only when there's more to see) page through it.
 */

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";

export interface CarouselItem {
  showId: string;
  date: string; // already formatted for display
  venue?: string | null;
  location?: string | null;
  image?: string | null;
  trailing?: string | null; // e.g. "315 ♥" / play count
}

function Card({ item }: { item: CarouselItem }) {
  const [broken, setBroken] = useState(false);
  const src = item.image && !broken ? item.image : "/logo.png";
  const isLogo = src === "/logo.png";
  return (
    <Link
      href={`/shows/${item.showId}`}
      className="flex w-40 flex-shrink-0 snap-start flex-col gap-2 rounded-lg p-2 transition hover:bg-white/10"
    >
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={src}
        alt=""
        loading="lazy"
        referrerPolicy="no-referrer"
        onError={() => setBroken(true)}
        className={`aspect-square w-full rounded-md bg-white/5 shadow-md ${
          isLogo ? "object-contain p-5 opacity-70" : "object-cover"
        }`}
      />
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-white">{item.date}</p>
        {item.venue && (
          <p className="truncate text-xs text-white/50">{item.venue}</p>
        )}
        {item.location && (
          <p className="truncate text-xs text-white/40">{item.location}</p>
        )}
        {item.trailing && (
          <p className="truncate text-xs text-deadly-star">{item.trailing}</p>
        )}
      </div>
    </Link>
  );
}

function Arrow({
  dir,
  show,
  onClick,
}: {
  dir: "left" | "right";
  show: boolean;
  onClick: () => void;
}) {
  return (
    <button
      aria-label={dir === "left" ? "Scroll left" : "Scroll right"}
      tabIndex={show ? 0 : -1}
      onClick={onClick}
      className={`absolute top-[34%] z-10 hidden h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full bg-black/70 text-white shadow-lg backdrop-blur-sm transition hover:bg-black hover:scale-105 sm:flex ${
        dir === "left" ? "left-0" : "right-0"
      } ${
        show
          ? "opacity-0 group-hover:opacity-100"
          : "pointer-events-none opacity-0"
      }`}
    >
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
        {dir === "left" ? <path d="M15 18l-6-6 6-6" /> : <path d="M9 18l6-6-6-6" />}
      </svg>
    </button>
  );
}

export default function ShowCarousel({
  title,
  items,
}: {
  title: string;
  items: CarouselItem[];
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [canLeft, setCanLeft] = useState(false);
  const [canRight, setCanRight] = useState(false);

  const update = useCallback(() => {
    const el = ref.current;
    if (!el) return;
    setCanLeft(el.scrollLeft > 4);
    setCanRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 4);
  }, []);

  useEffect(() => {
    update();
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(update);
    ro.observe(el);
    window.addEventListener("resize", update);
    return () => {
      ro.disconnect();
      window.removeEventListener("resize", update);
    };
  }, [update, items]);

  const page = (dir: -1 | 1) => {
    const el = ref.current;
    if (!el) return;
    el.scrollBy({ left: dir * el.clientWidth * 0.85, behavior: "smooth" });
  };

  if (items.length === 0) return null;
  return (
    <section className="group relative mb-6">
      <h3 className="mb-2 text-lg font-bold text-white">{title}</h3>
      <Arrow dir="left" show={canLeft} onClick={() => page(-1)} />
      <Arrow dir="right" show={canRight} onClick={() => page(1)} />
      <div
        ref={ref}
        onScroll={update}
        className="-mx-1 flex snap-x gap-1 overflow-x-auto px-1 pb-1 [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden"
      >
        {items.map((it) => (
          <Card key={it.showId} item={it} />
        ))}
      </div>
    </section>
  );
}
