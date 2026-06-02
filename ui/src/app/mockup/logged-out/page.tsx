"use client";

/**
 * QUICK-AND-DIRTY MOCKUP — logged-OUT Spotify-style three-pane.
 *
 * Companion to /mockup (the signed-in view). Same self-contained throwaway
 * rules: hardcoded data, no fetching, fixed full-bleed overlay.
 *
 * The Spotify logged-out pattern: browsing stays open, but the personal
 * surfaces flip to conversion.
 *   left   = "Your Library"  -> gated prompt: log in to pin / favorite / review
 *   middle = home / browse    -> unchanged, fully public (2,300+ shows)
 *   right  = "Get the app"    -> download + "log in to sync" teaser
 *   bottom = global sign-up banner (replaces the now-playing transport)
 */

import { useState } from "react";
import MockupSwitch from "../_switch";

// ---- sample data (browse is public) ---------------------------------------

type Show = { id: string; date: string; venue: string; city: string; rating?: number };

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
  { id: "1978-04-24", date: "Apr 24, 1978", venue: "Horton Field House", city: "Normal, IL", rating: 4.7 },
  { id: "1981-05-06", date: "May 6, 1981", venue: "Nassau Coliseum", city: "Uniondale, NY", rating: 4.4 },
];

const DECADES = ["60s", "70s", "80s", "90s"];
const LIB_FILTERS = ["Pinned", "Favorites", "Recent", "Reviews"];

// ---- bits ------------------------------------------------------------------

function Artwork({ label, size = "h-12 w-12" }: { label: string; size?: string }) {
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

function LeftLibraryGated() {
  return (
    <aside className="flex w-[300px] flex-shrink-0 flex-col rounded-lg bg-deadly-surface">
      <div className="flex items-center justify-between px-4 pt-4">
        <span className="flex items-center gap-2 text-sm font-bold text-white/70">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M3 5h18v2H3zM3 11h18v2H3zM3 17h12v2H3z" />
          </svg>
          Your Library
        </span>
      </div>

      {/* filter pills shown but inert/dimmed — there's nothing to filter yet */}
      <div className="flex gap-2 px-4 pt-3 opacity-40">
        {LIB_FILTERS.map((f) => (
          <span key={f} className="rounded-full bg-white/10 px-3 py-1 text-xs font-medium text-white/80">
            {f}
          </span>
        ))}
      </div>

      {/* gated prompt cards (Spotify's "Create your first playlist" pattern) */}
      <div className="flex flex-col gap-4 p-4 pt-5">
        <div className="rounded-lg bg-white/5 p-4">
          <p className="text-sm font-bold text-white">Save your favorite shows</p>
          <p className="mt-1 text-sm text-white/60">
            Log in to pin shows, build a favorites list, and pick up where you left off.
          </p>
          <button className="mt-3 rounded-full bg-white px-4 py-1.5 text-sm font-bold text-black hover:scale-[1.03]">
            Log in
          </button>
        </div>

        <div className="rounded-lg bg-white/5 p-4">
          <p className="text-sm font-bold text-white">Write reviews &amp; rate</p>
          <p className="mt-1 text-sm text-white/60">
            Share what you think of a performance and tag the best versions.
          </p>
          <button className="mt-3 rounded-full border border-white/30 px-4 py-1.5 text-sm font-bold text-white hover:border-white">
            Sign up free
          </button>
        </div>
      </div>

      <div className="mt-auto px-4 pb-4 text-[11px] leading-relaxed text-white/40">
        Everything you save syncs to the mobile app —{" "}
        <span className="text-white/60">iOS 2.32+ or Android 2.31+</span>.
      </div>
    </aside>
  );
}

function MiddleHome() {
  return (
    <main className="flex-1 overflow-y-auto rounded-lg bg-gradient-to-b from-deadly-surface to-deadly-bg">
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
        {/* logged-out: auth actions live up here */}
        <button className="text-sm font-bold text-white/70 hover:text-white">Sign up</button>
        <button className="rounded-full bg-white px-5 py-2 text-sm font-bold text-black hover:scale-105">
          Log in
        </button>
      </div>

      <div className="px-6 pb-10">
        <h1 className="mb-1 mt-2 text-2xl font-bold">Every Grateful Dead concert</h1>
        <p className="mb-5 text-sm text-white/50">
          2,300+ shows · 1965–1995 · free to browse, no account needed.
        </p>

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
          {TOP_RATED.map((s) => <Card key={s.id} show={s} />)}
        </Section>

        <Section title="Browse all shows">
          {BROWSE.map((s) => <Card key={s.id} show={s} />)}
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
  const [hover, setHover] = useState(false);
  return (
    <div
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      className="group relative rounded-lg bg-deadly-surface p-3 text-left transition hover:bg-white/10"
    >
      <div className="relative mb-3 aspect-square w-full rounded-md bg-gradient-to-br from-deadly-accent/40 to-deadly-blue/20 shadow-lg">
        {/* hover: play is open; the heart prompts login */}
        <button
          className={`absolute bottom-2 right-2 flex h-10 w-10 items-center justify-center rounded-full bg-deadly-accent text-black shadow-xl transition ${
            hover ? "translate-y-0 opacity-100" : "translate-y-2 opacity-0"
          }`}
          title="Play preview"
        >
          ▶
        </button>
        <button
          className={`absolute left-2 top-2 text-lg transition ${hover ? "opacity-100" : "opacity-0"} text-white/70 hover:text-deadly-accent`}
          title="Log in to save"
        >
          ♡
        </button>
      </div>
      <p className="truncate text-sm font-bold">{show.date}</p>
      <p className="truncate text-xs text-white/50">{show.venue}</p>
      <p className="mt-1 truncate text-xs text-white/40">{show.city}</p>
      {show.rating && <div className="mt-1"><Stars rating={show.rating} /></div>}
    </div>
  );
}

function RightApp() {
  return (
    <aside className="flex w-[320px] flex-shrink-0 flex-col gap-3">
      <div className="rounded-lg bg-gradient-to-b from-deadly-blue/25 to-deadly-surface p-5">
        <h3 className="text-lg font-bold">Make it yours</h3>
        <p className="mt-1 text-sm text-white/70">
          Create a free account to pin shows, save favorites, write reviews, and
          keep your recently played.
        </p>
        <button className="mt-4 w-full rounded-full bg-deadly-accent px-4 py-2.5 text-sm font-bold text-white hover:scale-[1.02]">
          Sign up free
        </button>
        <button className="mt-2 w-full text-sm font-semibold text-white/60 hover:text-white">
          Log in
        </button>
      </div>

      <div className="rounded-lg bg-gradient-to-b from-deadly-accent/25 to-deadly-surface p-5">
        <h3 className="text-lg font-bold">Take it with you</h3>
        <p className="mt-1 text-sm text-white/70">
          Download for your device. Sign in once and your library syncs everywhere.
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
    </aside>
  );
}

// ---- page ------------------------------------------------------------------

export default function MockupLoggedOutPage() {
  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-black text-white">
      <header className="flex flex-shrink-0 items-center gap-3 px-4 py-2">
        <span className="flex items-center gap-2 font-bold">
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-deadly-accent">⚡</span>
          The Deadly
        </span>
        <div className="ml-auto"><MockupSwitch /></div>
      </header>

      <div className="flex min-h-0 flex-1 gap-2 px-2 pb-2">
        <LeftLibraryGated />
        <MiddleHome />
        <RightApp />
      </div>

      {/* global sign-up banner — replaces the now-playing transport when
          signed out (Spotify's "Preview of Spotify" bar). */}
      <footer className="flex flex-shrink-0 items-center gap-4 bg-gradient-to-r from-deadly-blue to-deadly-accent px-6 py-3">
        <div className="min-w-0">
          <p className="text-xs font-bold uppercase tracking-wider text-white/80">
            Preview of The Deadly
          </p>
          <p className="truncate text-sm font-semibold text-white">
            Sign up to save favorites, write reviews, and sync to your phone.
          </p>
        </div>
        <button className="ml-auto flex-shrink-0 rounded-full bg-white px-6 py-2.5 text-sm font-bold text-black hover:scale-105">
          Sign up free
        </button>
      </footer>
    </div>
  );
}
