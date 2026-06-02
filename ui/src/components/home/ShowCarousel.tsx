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
  const src = item.image && !broken ? item.image : "/cover-fallback.png";
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
        className="aspect-square w-full rounded-md bg-white/5 object-cover shadow-md"
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
  const left = dir === "left";
  return (
    <button
      aria-label={left ? "Scroll left" : "Scroll right"}
      tabIndex={show ? 0 : -1}
      onClick={onClick}
      // Full-height, wide gutter: a big click target that fades in on hover and
      // only intercepts pointer events while shown (so edge cards stay clickable
      // otherwise). The gradient lets the cards behind fade rather than vanish.
      className={`absolute inset-y-0 z-10 hidden w-14 items-center px-2 transition sm:flex ${
        left
          ? "left-0 justify-start bg-gradient-to-r"
          : "right-0 justify-end bg-gradient-to-l"
      } from-deadly-bg via-deadly-bg/70 to-transparent ${
        show
          ? "pointer-events-none opacity-0 group-hover:pointer-events-auto group-hover:opacity-100"
          : "pointer-events-none opacity-0"
      }`}
    >
      <span className="flex h-9 w-9 items-center justify-center rounded-full bg-black/80 text-white shadow-lg transition hover:scale-110">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
          {left ? <path d="M15 18l-6-6 6-6" /> : <path d="M9 18l6-6-6-6" />}
        </svg>
      </span>
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
    <section className="mb-6">
      <h3 className="mb-2 text-lg font-bold text-white">{title}</h3>
      <div className="group relative">
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
      </div>
    </section>
  );
}
