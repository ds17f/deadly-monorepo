"use client";

/**
 * QUICK-AND-DIRTY MOCKUP — show detail on MOBILE.
 *
 * The web three-pane liner-notes layout (/mockup/show) collapses to a single
 * vertical scroll inside a phone frame: hero → action row → setlist →
 * collapsible "liner notes" sections (the AI content that isn't in the app
 * today but could be). A sticky mini-player pins to the bottom.
 *
 * Self-contained throwaway: hardcoded Cornell '77, no fetching.
 */

import { useState } from "react";
import Link from "next/link";
import MockupSwitch from "../_switch";

// ---- sample data: Barton Hall, May 8 1977 ---------------------------------

const SETLIST: { set: string; songs: { name: string; len: string; hl?: boolean }[] }[] = [
  {
    set: "Set 1",
    songs: [
      { name: "New Minglewood Blues", len: "5:21" },
      { name: "Loser", len: "7:48" },
      { name: "El Paso", len: "4:33" },
      { name: "Jack Straw", len: "5:02", hl: true },
    ],
  },
  {
    set: "Set 2",
    songs: [
      { name: "Scarlet Begonias", len: "9:32", hl: true },
      { name: "Fire on the Mountain", len: "14:18", hl: true },
      { name: "Estimated Prophet", len: "9:05" },
      { name: "Morning Dew", len: "13:09", hl: true },
    ],
  },
];

const HIGHLIGHTS = [
  "Scarlet > Fire is the definitive version",
  "Morning Dew builds to a cathartic peak",
  "Pristine Betty Board soundboard source",
];
const SEQUENCES = [
  ["Scarlet Begonias", "Fire on the Mountain"],
  ["St. Stephen", "Morning Dew"],
];
const BAND = [
  { who: "Jerry Garcia", note: "Fluid, melodic leads — especially on Dew" },
  { who: "Bob Weir", note: "Crisp rhythm, strong Estimated" },
  { who: "Keith Godchaux", note: "Lush piano fills throughout Scarlet" },
];
const LINEUP = ["Jerry Garcia", "Bob Weir", "Phil Lesh", "Keith Godchaux", "Donna Godchaux", "Mickey Hart", "Bill Kreutzmann"];

// ---- collapsible section --------------------------------------------------

function Collapsible({
  title,
  badge,
  defaultOpen = false,
  children,
}: {
  title: string;
  badge?: string;
  defaultOpen?: boolean;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border-b border-white/10">
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center justify-between px-4 py-3.5 text-left"
      >
        <span className="flex items-center gap-2 text-sm font-bold text-white">
          {title}
          {badge && (
            <span className="rounded-full bg-white/10 px-2 py-0.5 text-[10px] font-medium text-white/60">
              {badge}
            </span>
          )}
        </span>
        <span className={`text-white/40 transition ${open ? "rotate-180" : ""}`}>▾</span>
      </button>
      {open && <div className="px-4 pb-4">{children}</div>}
    </div>
  );
}

// ---- phone screen ---------------------------------------------------------

function PhoneScreen() {
  return (
    <div className="flex h-full flex-col bg-deadly-bg">
      {/* scrollable body */}
      <div className="flex-1 overflow-y-auto pb-2">
        {/* hero */}
        <div className="bg-gradient-to-b from-deadly-accent/40 to-deadly-bg px-4 pb-4 pt-3">
          <div className="flex items-center justify-between py-2 text-white/70">
            <button className="text-xl">‹</button>
            <span className="text-xs font-medium">Cornell &apos;77</span>
            <button className="text-xl">⋯</button>
          </div>
          <div className="mx-auto mt-2 h-40 w-40 rounded-md bg-gradient-to-br from-deadly-accent/60 to-deadly-blue/40 shadow-2xl" />
          <h1 className="mt-4 text-center text-2xl font-extrabold leading-tight">May 8, 1977</h1>
          <p className="mt-1 text-center text-sm font-semibold text-white/90">Barton Hall, Cornell University</p>
          <p className="text-center text-xs text-white/60">Ithaca, NY</p>
          <p className="mt-2 flex flex-wrap items-center justify-center gap-1.5 text-[11px] text-white/60">
            <span className="text-deadly-star">★ 4.9</span>
            <span>·</span>
            <span className="rounded bg-white/10 px-1.5 py-0.5">🤖 4.8</span>
            <span>·</span>
            <span className="rounded bg-deadly-blue/30 px-1.5 py-0.5 text-white/80">SBD</span>
            <span>·</span>
            <span>14 recs</span>
          </p>
        </div>

        {/* action row */}
        <div className="flex items-center justify-center gap-6 px-4 py-4">
          <button className="text-2xl text-white/60 hover:text-deadly-accent">♡</button>
          <button className="text-xl text-white/60">⤓</button>
          <button className="flex h-14 w-14 items-center justify-center rounded-full bg-deadly-accent text-2xl text-black shadow-xl">▶</button>
          <button className="text-xl text-white/60">⇄</button>
          <button className="text-xl text-white/60">⤴</button>
        </div>

        {/* source picker chip */}
        <div className="px-4 pb-2">
          <button className="flex w-full items-center justify-between rounded-lg border border-white/15 px-3 py-2 text-xs text-white/70">
            <span>Source: <span className="font-semibold text-white">SBD · Betty Board</span></span>
            <span>Switch (14) ▾</span>
          </button>
        </div>

        {/* setlist */}
        <div className="px-4 py-2">
          {SETLIST.map((set) => (
            <div key={set.set} className="mb-3">
              <h2 className="mb-1 text-[11px] font-bold uppercase tracking-wider text-white/40">{set.set}</h2>
              {set.songs.map((song, i) => (
                <div key={song.name} className="flex items-center gap-3 rounded-md px-1 py-2 active:bg-white/10">
                  <span className="w-4 text-right text-xs text-white/40">{i + 1}</span>
                  <span className={`flex-1 truncate text-sm ${song.hl ? "font-semibold text-white" : "text-white/80"}`}>
                    {song.name}
                    {song.hl && <span className="ml-1.5 text-[10px] text-deadly-star">★</span>}
                  </span>
                  <span className="text-[11px] text-white/40">{song.len}</span>
                </div>
              ))}
            </div>
          ))}
        </div>

        {/* LINER NOTES — collapsible sections (the AI content) */}
        <div className="mt-1">
          <p className="px-4 pb-1 pt-2 text-[11px] font-bold uppercase tracking-wider text-deadly-title/70">
            Liner notes
          </p>

          <Collapsible title="About this show" defaultOpen>
            <p className="mb-2 text-sm text-white/80">
              The legendary Barton Hall performance — for many the single
              greatest night the Dead ever played.
            </p>
            <p className="text-xs leading-relaxed text-white/55">
              The second set earns the myth: a Scarlet Begonias that melts into
              a soaring Fire on the Mountain, then a Morning Dew that builds to
              one of the most cathartic peaks in the band&apos;s history.
            </p>
            <p className="mt-2 text-[10px] uppercase tracking-wider text-white/30">🤖 AI review · confidence high</p>
          </Collapsible>

          <Collapsible title="Key highlights" badge="3">
            <ul className="space-y-1.5 text-sm text-white/70">
              {HIGHLIGHTS.map((h) => (
                <li key={h} className="flex gap-2"><span className="text-deadly-highlight">•</span>{h}</li>
              ))}
            </ul>
          </Collapsible>

          <Collapsible title="Must-listen sequences" badge="2">
            <div className="flex flex-col gap-2">
              {SEQUENCES.map((seq, i) => (
                <button key={i} className="flex items-center gap-2 rounded-lg bg-white/5 px-3 py-2 text-left text-sm text-white/80 active:bg-white/10">
                  <span className="text-deadly-accent">▶</span>{seq.join(" → ")}
                </button>
              ))}
            </div>
          </Collapsible>

          <Collapsible title="Band performance">
            <div className="space-y-2.5">
              {BAND.map((b) => (
                <div key={b.who}>
                  <p className="text-sm font-semibold text-white">{b.who}</p>
                  <p className="text-xs text-white/55">{b.note}</p>
                </div>
              ))}
            </div>
          </Collapsible>

          <Collapsible title="Lineup" badge="7">
            <div className="flex flex-wrap gap-1.5">
              {LINEUP.map((m) => (
                <span key={m} className="rounded-full bg-white/5 px-2.5 py-1 text-xs text-white/70">{m}</span>
              ))}
            </div>
          </Collapsible>

          <Collapsible title="Your review">
            <button className="w-full rounded-lg border border-dashed border-white/20 px-3 py-3 text-sm text-white/50 active:bg-white/5">
              ＋ Rate &amp; review this show
            </button>
          </Collapsible>
        </div>
      </div>

      {/* sticky mini-player */}
      <div className="flex items-center gap-3 border-t border-white/10 bg-deadly-surface px-3 py-2.5">
        <span className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded bg-gradient-to-br from-deadly-accent/40 to-deadly-blue/20 text-xs">⚡</span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-xs font-semibold">Fire on the Mountain</p>
          <p className="truncate text-[10px] text-white/50">Cornell &apos;77 · SBD</p>
        </div>
        <button className="text-deadly-accent">♥</button>
        <button className="text-2xl">⏸</button>
      </div>

      {/* tab bar */}
      <div className="flex items-center justify-around border-t border-white/10 bg-deadly-bg py-2 text-[10px] text-white/40">
        {[["⌂", "Home"], ["🔍", "Search"], ["⚡", "Library"], ["☰", "Settings"]].map(([icon, label], i) => (
          <span key={label} className={`flex flex-col items-center gap-0.5 ${i === 2 ? "text-white" : ""}`}>
            <span className="text-base">{icon}</span>
            {label}
          </span>
        ))}
      </div>
    </div>
  );
}

// ---- page ------------------------------------------------------------------

export default function MockupMobilePage() {
  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-black text-white">
      <header className="flex flex-shrink-0 items-center gap-3 px-4 py-2">
        <Link href="/mockup" className="flex items-center gap-2 font-bold">
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-deadly-accent">⚡</span>
          The Deadly
        </Link>
        <span className="text-white/30">/</span>
        <span className="text-sm text-white/60">Mobile · liner notes</span>
        <div className="ml-auto"><MockupSwitch /></div>
      </header>

      {/* phone frame on a desk-y backdrop */}
      <div className="flex min-h-0 flex-1 items-center justify-center bg-gradient-to-b from-deadly-surface/40 to-black p-4">
        <div className="flex flex-col items-center gap-4">
          <div className="relative h-[760px] w-[380px] overflow-hidden rounded-[44px] border-[10px] border-neutral-800 bg-black shadow-2xl">
            {/* notch */}
            <div className="absolute left-1/2 top-0 z-20 h-6 w-32 -translate-x-1/2 rounded-b-2xl bg-neutral-800" />
            <div className="h-full w-full overflow-hidden rounded-[34px]">
              <PhoneScreen />
            </div>
          </div>
          <p className="max-w-sm text-center text-xs text-white/40">
            The web three-pane liner-notes rail collapses into tap-to-expand
            sections on mobile. The AI review, highlights, sequences, band
            breakdown, and lineup all come along.
          </p>
        </div>
      </div>
    </div>
  );
}
