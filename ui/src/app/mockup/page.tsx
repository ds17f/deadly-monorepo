"use client";

/**
 * QUICK-AND-DIRTY MOCKUP — Spotify-style three-pane layout for The Deadly.
 *
 * Self-contained: hardcoded sample data, no data fetching, no providers.
 * Full-bleed fixed overlay so it escapes the max-w-5xl root <main>. This is
 * a throwaway design exploration at /mockup — not wired into the real app.
 *
 *   left   = "Your Library"  (pinned / favorites / recent / reviews)
 *   middle = home / browse    (greeting, top rated, year browse, list)
 *   right  = "Get the app"    (download panel + now-playing teaser)
 */

import { useState } from "react";
import MockupSwitch from "./_switch";

// ---- sample data -----------------------------------------------------------

type Show = {
  id: string;
  date: string;
  venue: string;
  city: string;
  rating?: number;
  image?: string;
};

const LIBRARY: (Show & { kind: "pinned" | "favorite" | "recent" | "review"; sub?: string })[] = [
  { id: "1977-05-08", date: "May 8, 1977", venue: "Barton Hall, Cornell U.", city: "Ithaca, NY", kind: "pinned", sub: "Pinned · Show" },
  { id: "1972-05-03", date: "May 3, 1972", venue: "Olympia Theatre", city: "Paris, France", kind: "favorite", sub: "Favorite · Show" },
  { id: "1969-02-27", date: "Feb 27, 1969", venue: "Fillmore West", city: "San Francisco, CA", kind: "review", sub: "Your review · ★ 5" },
  { id: "1974-06-18", date: "Jun 18, 1974", venue: "Freedom Hall", city: "Louisville, KY", kind: "recent", sub: "Recently played" },
  { id: "1989-07-07", date: "Jul 7, 1989", venue: "JFK Stadium", city: "Philadelphia, PA", kind: "favorite", sub: "Favorite · Show" },
  { id: "1973-11-11", date: "Nov 11, 1973", venue: "Winterland", city: "San Francisco, CA", kind: "recent", sub: "Recently played" },
  { id: "1977-05-22", date: "May 22, 1977", venue: "The Sportatorium", city: "Pembroke Pines, FL", kind: "review", sub: "Your review · ★ 4" },
  { id: "1968-08-21", date: "Aug 21, 1968", venue: "Fillmore West", city: "San Francisco, CA", kind: "recent", sub: "Recently played" },
];

const TOP_RATED: Show[] = [
  { id: "1977-05-08", date: "5/8/77", venue: "Barton Hall", city: "Ithaca, NY", rating: 4.9 },
  { id: "1972-05-03", date: "5/3/72", venue: "Olympia", city: "Paris", rating: 4.8 },
  { id: "1969-02-27", date: "2/27/69", venue: "Fillmore West", city: "SF, CA", rating: 4.8 },
  { id: "1974-06-28", date: "6/28/74", venue: "Boston Garden", city: "Boston, MA", rating: 4.7 },
];

const BROWSE: Show[] = [
  { id: "1965-12-04", date: "Dec 4, 1965", venue: "Big Beat Acid Test", city: "Palo Alto, CA", rating: 4.1 },
  { id: "1970-02-13", date: "Feb 13, 1970", venue: "Fillmore East", city: "New York, NY", rating: 4.6 },
  { id: "1971-04-29", date: "Apr 29, 1971", venue: "Fillmore East", city: "New York, NY", rating: 4.5 },
  { id: "1977-05-08", date: "May 8, 1977", venue: "Barton Hall, Cornell U.", city: "Ithaca, NY", rating: 4.9 },
  { id: "1978-04-24", date: "Apr 24, 1978", venue: "Normal, IL", city: "Horton Field House", rating: 4.7 },
  { id: "1981-05-06", date: "May 6, 1981", venue: "Nassau Coliseum", city: "Uniondale, NY", rating: 4.4 },
];

const DECADES = ["60s", "70s", "80s", "90s"];
const LIB_FILTERS = ["Pinned", "Favorites", "Recent", "Reviews"];

// ---- bits ------------------------------------------------------------------

function Artwork({ label, size = "h-12 w-12" }: { label: string; size?: string }) {
  // No catalog art in the mock — show a logo-tinted tile (mirrors the real
  // logo fallback in ShowArtwork).
  return (
    <span
      className={`flex ${size} flex-shrink-0 items-center justify-center rounded-md bg-gradient-to-br from-deadly-accent/30 to-deadly-blue/20 text-[10px] font-bold text-white/70`}
    >
      <span className="opacity-70">⚡</span>
      <span className="sr-only">{label}</span>
    </span>
  );
}

function Stars({ rating }: { rating: number }) {
  return (
    <span className="text-xs text-deadly-star">
      ★ <span className="text-white/50">{rating.toFixed(1)}</span>
    </span>
  );
}

// ---- columns ---------------------------------------------------------------

function LeftLibrary() {
  const [active, setActive] = useState<string | null>(null);
  const shown = active
    ? LIBRARY.filter((s) =>
        active === "Pinned" ? s.kind === "pinned"
        : active === "Favorites" ? s.kind === "favorite"
        : active === "Recent" ? s.kind === "recent"
        : s.kind === "review")
    : LIBRARY;

  return (
    <aside className="flex w-[300px] flex-shrink-0 flex-col rounded-lg bg-deadly-surface">
      <div className="flex items-center justify-between px-4 pt-4">
        <button className="flex items-center gap-2 text-sm font-bold text-white/70 hover:text-white">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M3 5h18v2H3zM3 11h18v2H3zM3 17h12v2H3z" />
          </svg>
          Your Library
        </button>
        <button className="flex h-7 w-7 items-center justify-center rounded-full text-lg text-white/60 hover:bg-white/10 hover:text-white">
          +
        </button>
      </div>

      <div className="flex gap-2 px-4 pt-3">
        {LIB_FILTERS.map((f) => (
          <button
            key={f}
            onClick={() => setActive(active === f ? null : f)}
            className={`rounded-full px-3 py-1 text-xs font-medium transition ${
              active === f
                ? "bg-white text-black"
                : "bg-white/10 text-white/80 hover:bg-white/20"
            }`}
          >
            {f}
          </button>
        ))}
      </div>

      <div className="flex items-center justify-between px-4 pb-2 pt-3 text-white/50">
        <button className="flex items-center gap-1 text-xs hover:text-white">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
            <path d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 10-.7.7l.27.28v.79l5 4.99L20.49 19zm-6 0A4.5 4.5 0 1114 9.5 4.5 4.5 0 019.5 14z" />
          </svg>
          Search
        </button>
        <button className="text-xs hover:text-white">Recents ▾</button>
      </div>

      <div className="flex-1 overflow-y-auto px-2 pb-2">
        {shown.map((s) => (
          <button
            key={s.id + s.kind}
            className="flex w-full items-center gap-3 rounded-md px-2 py-2 text-left transition hover:bg-white/10"
          >
            <Artwork label={s.date} />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-white">{s.date}</p>
              <p className="truncate text-xs text-white/50">{s.sub} · {s.city}</p>
            </div>
            {s.kind === "pinned" && (
              <span className="text-deadly-accent" title="Pinned">📌</span>
            )}
          </button>
        ))}
      </div>
    </aside>
  );
}

function MiddleHome() {
  return (
    <main className="flex-1 overflow-y-auto rounded-lg bg-gradient-to-b from-deadly-surface to-deadly-bg">
      {/* sticky top bar */}
      <div className="sticky top-0 z-10 flex items-center gap-3 rounded-t-lg bg-deadly-surface/80 px-6 py-3 backdrop-blur">
        <div className="flex flex-1 items-center gap-2 rounded-full bg-deadly-bg px-4 py-2">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" className="text-white/50">
            <path d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 10-.7.7l.27.28v.79l5 4.99L20.49 19zm-6 0A4.5 4.5 0 1114 9.5 4.5 4.5 0 019.5 14z" />
          </svg>
          <input
            placeholder="Search 2,300+ shows by date, venue, or city"
            className="w-full bg-transparent text-sm text-white placeholder:text-white/40 focus:outline-none"
          />
        </div>
        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-deadly-accent text-sm font-bold">D</span>
      </div>

      <div className="px-6 pb-10">
        <h1 className="mb-4 mt-2 text-2xl font-bold">Good evening, Deadhead</h1>

        {/* quick-access tiles */}
        <div className="mb-8 grid grid-cols-2 gap-3 lg:grid-cols-3">
          {TOP_RATED.map((s) => (
            <div
              key={s.id}
              className="group flex items-center gap-3 overflow-hidden rounded-md bg-white/10 pr-3 transition hover:bg-white/20"
            >
              <Artwork label={s.date} size="h-14 w-14" />
              <div className="min-w-0">
                <p className="truncate text-sm font-bold">{s.date}</p>
                <p className="truncate text-xs text-white/50">{s.city}</p>
              </div>
            </div>
          ))}
        </div>

        {/* decade chips */}
        <div className="mb-6 flex flex-wrap gap-2">
          {DECADES.map((d) => (
            <button key={d} className="rounded-full border border-white/15 px-4 py-1.5 text-sm text-white/80 hover:border-white/40 hover:text-white">
              {d}
            </button>
          ))}
          <button className="rounded-full border border-white/15 px-4 py-1.5 text-sm text-white/80 hover:border-white/40 hover:text-white">
            Soundboards
          </button>
        </div>

        <Section title="Top rated">
          {TOP_RATED.map((s) => (
            <Card key={s.id} show={s} />
          ))}
        </Section>

        <Section title="Browse all shows">
          {BROWSE.map((s) => (
            <Card key={s.id} show={s} />
          ))}
        </Section>
      </div>
    </main>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-8">
      <div className="mb-3 flex items-baseline justify-between">
        <h2 className="text-lg font-bold">{title}</h2>
        <button className="text-xs font-semibold text-white/50 hover:text-white">Show all</button>
      </div>
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">{children}</div>
    </section>
  );
}

function Card({ show }: { show: Show }) {
  return (
    <button className="group rounded-lg bg-deadly-surface p-3 text-left transition hover:bg-white/10">
      <div className="mb-3 aspect-square w-full rounded-md bg-gradient-to-br from-deadly-accent/40 to-deadly-blue/20 shadow-lg" />
      <p className="truncate text-sm font-bold">{show.date}</p>
      <p className="truncate text-xs text-white/50">{show.venue}</p>
      <p className="mt-1 truncate text-xs text-white/40">{show.city}</p>
      {show.rating && <div className="mt-1"><Stars rating={show.rating} /></div>}
    </button>
  );
}

function RightApp() {
  return (
    <aside className="flex w-[320px] flex-shrink-0 flex-col gap-3">
      {/* now playing teaser */}
      <div className="flex flex-col rounded-lg bg-deadly-surface p-4">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-sm font-bold">Now Playing</h3>
          <button className="text-white/40 hover:text-white">✕</button>
        </div>
        <div className="aspect-square w-full rounded-md bg-gradient-to-br from-deadly-accent/50 to-deadly-blue/30 shadow-xl" />
        <p className="mt-3 text-base font-bold">Scarlet Begonias →</p>
        <p className="text-sm text-white/60">Barton Hall · May 8, 1977</p>
        <div className="mt-4 flex items-center gap-2 text-xs text-white/40">
          <span>2:14</span>
          <div className="h-1 flex-1 rounded-full bg-white/15">
            <div className="h-1 w-1/3 rounded-full bg-white" />
          </div>
          <span>9:32</span>
        </div>
      </div>

      {/* get the app */}
      <div className="rounded-lg bg-gradient-to-b from-deadly-accent/25 to-deadly-surface p-5">
        <h3 className="text-lg font-bold">Take it with you</h3>
        <p className="mt-1 text-sm text-white/70">
          Download for your device. Your library, favorites, and reviews sync
          everywhere.
        </p>
        <div className="mt-4 flex flex-col gap-2">
          <button className="flex items-center justify-center gap-2 rounded-full bg-black/60 px-4 py-2.5 text-sm font-semibold ring-1 ring-white/20 hover:bg-black">
             App Store
          </button>
          <button className="flex items-center justify-center gap-2 rounded-full bg-black/60 px-4 py-2.5 text-sm font-semibold ring-1 ring-white/20 hover:bg-black">
            ▶ Google Play
          </button>
        </div>
        <p className="mt-3 text-[11px] text-white/40">
          Sync requires iOS 2.32+ or Android 2.31+.
        </p>
      </div>

      {/* about / collection */}
      <div className="rounded-lg bg-deadly-surface p-5 text-sm text-white/60">
        <h3 className="mb-1 text-sm font-bold text-white">Cornell &apos;77</h3>
        <p>The legendary Barton Hall show — widely cited as the definitive Dead performance. Part of the &quot;Essential Shows&quot; collection.</p>
        <button className="mt-3 text-xs font-semibold text-deadly-blue hover:underline">View collection →</button>
      </div>
    </aside>
  );
}

// ---- page ------------------------------------------------------------------

export default function MockupPage() {
  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-black text-white">
      {/* global top nav */}
      <header className="flex flex-shrink-0 items-center gap-3 px-4 py-2">
        <span className="flex items-center gap-2 font-bold">
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-deadly-accent">⚡</span>
          The Deadly
        </span>
        <div className="ml-auto"><MockupSwitch /></div>
      </header>

      {/* three panes */}
      <div className="flex min-h-0 flex-1 gap-2 px-2 pb-2">
        <LeftLibrary />
        <MiddleHome />
        <RightApp />
      </div>

      {/* bottom now-playing bar (Spotify-style) */}
      <footer className="flex flex-shrink-0 items-center gap-4 px-4 py-3">
        <div className="flex w-[300px] items-center gap-3">
          <Artwork label="now playing" />
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">Scarlet Begonias</p>
            <p className="truncate text-xs text-white/50">Cornell &apos;77</p>
          </div>
          <span className="text-white/40 hover:text-deadly-accent">♥</span>
        </div>
        <div className="flex flex-1 flex-col items-center gap-1">
          <div className="flex items-center gap-5 text-white/80">
            <button className="hover:text-white">⏮</button>
            <button className="flex h-9 w-9 items-center justify-center rounded-full bg-white text-black hover:scale-105">▶</button>
            <button className="hover:text-white">⏭</button>
          </div>
          <div className="flex w-full max-w-md items-center gap-2 text-[11px] text-white/40">
            <span>2:14</span>
            <div className="h-1 flex-1 rounded-full bg-white/15">
              <div className="h-1 w-1/3 rounded-full bg-white" />
            </div>
            <span>9:32</span>
          </div>
        </div>
        <div className="flex w-[300px] items-center justify-end gap-3 text-white/50">
          <button className="hover:text-white">🔊</button>
          <div className="h-1 w-24 rounded-full bg-white/15">
            <div className="h-1 w-2/3 rounded-full bg-white/60" />
          </div>
        </div>
      </footer>
    </div>
  );
}
