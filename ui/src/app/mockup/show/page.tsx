"use client";

/**
 * QUICK-AND-DIRTY MOCKUP — show DETAIL in the Spotify three-pane world.
 *
 * The thesis: a focused show is Spotify's "album view" — big gradient hero +
 * tracklist in the middle. But The Deadly has editorial content Spotify
 * structurally lacks (AI review, must-listen sequences, band performance,
 * lineup, recording picker). That becomes a persistent "liner notes" RIGHT
 * RAIL — the thing that makes this not just a Spotify clone.
 *
 *   left   = "Your Library"  (unchanged shell)
 *   middle = album-style hero + setlist/tracklist + long-form AI review
 *   right  = liner notes      (AI highlights, sequences, band, lineup, rec)
 *
 * Self-contained throwaway: hardcoded Cornell '77, no fetching.
 */

import Link from "next/link";
import MockupSwitch from "../_switch";

// ---- sample data: Barton Hall, May 8 1977 ---------------------------------

const SHOW = {
  date: "May 8, 1977",
  venue: "Barton Hall, Cornell University",
  city: "Ithaca, NY",
  rating: 4.9,
  aiRating: 4.8,
  sources: ["SBD", "MATRIX"],
  recordings: 14,
};

const SETLIST: { set: string; songs: { name: string; len: string; highlight?: boolean }[] }[] = [
  {
    set: "Set 1",
    songs: [
      { name: "New Minglewood Blues", len: "5:21" },
      { name: "Loser", len: "7:48" },
      { name: "El Paso", len: "4:33" },
      { name: "They Love Each Other", len: "6:55" },
      { name: "Jack Straw", len: "5:02", highlight: true },
    ],
  },
  {
    set: "Set 2",
    songs: [
      { name: "Scarlet Begonias", len: "9:32", highlight: true },
      { name: "Fire on the Mountain", len: "14:18", highlight: true },
      { name: "Estimated Prophet", len: "9:05" },
      { name: "St. Stephen", len: "5:44" },
      { name: "Morning Dew", len: "13:09", highlight: true },
    ],
  },
];

const HIGHLIGHTS = [
  "Scarlet > Fire is the definitive version — endlessly cited",
  "Morning Dew builds to a cathartic peak",
  "Pristine Betty Board soundboard source",
];

const SEQUENCES = [
  ["Scarlet Begonias", "Fire on the Mountain"],
  ["St. Stephen", "Morning Dew"],
];

const BAND: { who: string; note: string }[] = [
  { who: "Jerry Garcia", note: "Fluid, melodic leads — especially on Dew" },
  { who: "Bob Weir", note: "Crisp rhythm, strong Estimated" },
  { who: "Keith Godchaux", note: "Lush piano fills throughout Scarlet" },
  { who: "Phil Lesh", note: "Deep, exploratory bass runs" },
];

const LINEUP = ["Jerry Garcia", "Bob Weir", "Phil Lesh", "Keith Godchaux", "Donna Godchaux", "Mickey Hart", "Bill Kreutzmann"];

// ---- left rail (same shell) -----------------------------------------------

function LeftLibrary() {
  const items = [
    { d: "5/8/77", s: "Pinned · Cornell", pin: true },
    { d: "5/3/72", s: "Favorite · Paris" },
    { d: "2/27/69", s: "Review · ★5" },
    { d: "6/18/74", s: "Recently played" },
  ];
  return (
    <aside className="flex w-[260px] flex-shrink-0 flex-col rounded-lg bg-deadly-surface">
      <div className="flex items-center gap-2 px-4 pt-4 text-sm font-bold text-white/70">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
          <path d="M3 5h18v2H3zM3 11h18v2H3zM3 17h12v2H3z" />
        </svg>
        Your Library
      </div>
      <div className="flex flex-col gap-1 p-2 pt-3">
        {items.map((it) => (
          <button key={it.d} className="flex items-center gap-3 rounded-md px-2 py-2 text-left transition hover:bg-white/10">
            <span className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-md bg-gradient-to-br from-deadly-accent/30 to-deadly-blue/20 text-xs">⚡</span>
            <span className="min-w-0">
              <span className="block truncate text-sm font-semibold">{it.d}</span>
              <span className="block truncate text-xs text-white/50">{it.s}</span>
            </span>
            {it.pin && <span className="ml-auto text-deadly-accent">📌</span>}
          </button>
        ))}
      </div>
    </aside>
  );
}

// ---- middle: album-style hero + tracklist + review ------------------------

function Middle() {
  return (
    <main className="flex-1 overflow-y-auto rounded-lg bg-gradient-to-b from-deadly-accent/30 via-deadly-surface to-deadly-bg">
      {/* hero */}
      <div className="flex items-end gap-6 px-6 pb-6 pt-10">
        <div className="h-44 w-44 flex-shrink-0 rounded-md bg-gradient-to-br from-deadly-accent/60 to-deadly-blue/40 shadow-2xl" />
        <div className="min-w-0 pb-1">
          <p className="text-xs font-bold uppercase tracking-wider text-white/70">Concert</p>
          <h1 className="mt-1 text-4xl font-extrabold leading-tight">{SHOW.date}</h1>
          <p className="mt-2 text-lg font-semibold text-white/90">{SHOW.venue}</p>
          <p className="text-sm text-white/60">{SHOW.city}</p>
          <p className="mt-3 flex flex-wrap items-center gap-2 text-xs text-white/60">
            <span className="text-deadly-star">★ {SHOW.rating.toFixed(1)}</span>
            <span>·</span>
            <span className="rounded bg-white/10 px-2 py-0.5">🤖 AI {SHOW.aiRating.toFixed(1)}</span>
            <span>·</span>
            {SHOW.sources.map((s) => (
              <span key={s} className="rounded bg-deadly-blue/30 px-2 py-0.5 font-medium text-white/80">{s}</span>
            ))}
            <span>·</span>
            <span>{SHOW.recordings} recordings</span>
          </p>
        </div>
      </div>

      {/* action bar */}
      <div className="flex items-center gap-5 px-6 pb-6">
        <button className="flex h-14 w-14 items-center justify-center rounded-full bg-deadly-accent text-2xl text-black shadow-xl hover:scale-105">▶</button>
        <button className="text-3xl text-white/60 hover:text-deadly-accent" title="Favorite">♡</button>
        <button className="text-2xl text-white/60 hover:text-white" title="Download">⤓</button>
        <button className="text-2xl text-white/60 hover:text-white" title="Recording picker">⋯</button>
        <span className="ml-2 rounded-full border border-white/20 px-3 py-1.5 text-xs text-white/70">
          Source: SBD · Betty Board ▾
        </span>
      </div>

      {/* tracklist */}
      <div className="px-6 pb-10">
        {SETLIST.map((set) => (
          <div key={set.set} className="mb-6">
            <h2 className="mb-2 text-sm font-bold uppercase tracking-wider text-white/50">{set.set}</h2>
            <div className="flex flex-col">
              {set.songs.map((song, i) => (
                <button
                  key={song.name}
                  className="group flex items-center gap-4 rounded-md px-3 py-2 text-left transition hover:bg-white/10"
                >
                  <span className="w-4 text-right text-sm text-white/40 group-hover:hidden">{i + 1}</span>
                  <span className="hidden w-4 text-right text-sm text-white group-hover:inline">▶</span>
                  <span className={`flex-1 truncate text-sm font-medium ${song.highlight ? "text-white" : "text-white/80"}`}>
                    {song.name}
                    {song.highlight && (
                      <span className="ml-2 rounded bg-deadly-star/20 px-1.5 py-0.5 text-[10px] font-bold text-deadly-star">
                        HIGHLIGHT
                      </span>
                    )}
                  </span>
                  <span className="text-xs text-white/40">{song.len}</span>
                </button>
              ))}
            </div>
          </div>
        ))}

        {/* long-form AI review prose lives inline under the tracklist */}
        <section className="mt-2 max-w-2xl">
          <h2 className="mb-3 text-lg font-bold text-deadly-title">About this show</h2>
          <p className="mb-3 text-white/80">
            The legendary Barton Hall performance — for many the single
            greatest night the Grateful Dead ever played.
          </p>
          <p className="text-sm leading-relaxed text-white/60">
            From the opening Minglewood the band is locked in, but it&apos;s the
            second set that earns the myth: a Scarlet Begonias that melts
            seamlessly into a soaring Fire on the Mountain, then a Morning Dew
            that builds patiently to one of the most cathartic peaks in the
            band&apos;s history. The pristine Betty Board soundboard only
            heightens the case. If you listen to one 1977 show, make it this one.
          </p>
          <p className="mt-4 text-[11px] uppercase tracking-wider text-white/30">
            🤖 Generated review · confidence: high
          </p>
        </section>
      </div>
    </main>
  );
}

// ---- right: LINER NOTES (the differentiator) ------------------------------

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-lg bg-deadly-surface p-4">
      <h3 className="mb-3 text-xs font-bold uppercase tracking-wider text-deadly-title/80">
        {title}
        <span className="ml-2 inline-block h-px w-10 align-middle bg-white/20" />
      </h3>
      {children}
    </section>
  );
}

function RightLinerNotes() {
  return (
    <aside className="flex w-[340px] flex-shrink-0 flex-col gap-3 overflow-y-auto">
      <Panel title="Key highlights">
        <ul className="space-y-2 text-sm text-white/70">
          {HIGHLIGHTS.map((h) => (
            <li key={h} className="flex gap-2">
              <span className="text-deadly-highlight">•</span>
              {h}
            </li>
          ))}
        </ul>
      </Panel>

      <Panel title="Must-listen sequences">
        <div className="flex flex-col gap-2">
          {SEQUENCES.map((seq, i) => (
            <button
              key={i}
              className="flex items-center gap-2 rounded-lg bg-white/5 px-3 py-2 text-left text-sm text-white/80 transition hover:bg-white/10"
            >
              <span className="text-deadly-accent">▶</span>
              {seq.join(" → ")}
            </button>
          ))}
        </div>
      </Panel>

      <Panel title="Band performance">
        <div className="space-y-2.5">
          {BAND.map((b) => (
            <div key={b.who}>
              <p className="text-sm font-semibold text-white">{b.who}</p>
              <p className="text-xs text-white/55">{b.note}</p>
            </div>
          ))}
        </div>
      </Panel>

      <Panel title="Best recording">
        <p className="text-sm font-semibold text-white">gd1977-05-08.sbd.betty</p>
        <p className="mt-1 text-xs text-white/55">
          The Betty Cantor-Jackson soundboard — the cleanest, most complete
          source. Picked by the AI as the definitive listen.
        </p>
        <button className="mt-3 w-full rounded-full border border-white/20 px-3 py-1.5 text-xs font-semibold text-white/80 hover:border-white/40">
          Switch recording (14)
        </button>
      </Panel>

      <Panel title="Lineup">
        <div className="flex flex-wrap gap-1.5">
          {LINEUP.map((m) => (
            <span key={m} className="rounded-full bg-white/5 px-2.5 py-1 text-xs text-white/70">{m}</span>
          ))}
        </div>
      </Panel>
    </aside>
  );
}

// ---- page ------------------------------------------------------------------

export default function MockupShowPage() {
  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-black text-white">
      <header className="flex flex-shrink-0 items-center gap-3 px-4 py-2">
        <Link href="/mockup" className="flex items-center gap-2 font-bold">
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-deadly-accent">⚡</span>
          The Deadly
        </Link>
        <span className="text-white/30">/</span>
        <span className="text-sm text-white/60">Cornell &apos;77</span>
        <div className="ml-auto"><MockupSwitch /></div>
      </header>

      <div className="flex min-h-0 flex-1 gap-2 px-2 pb-2">
        <LeftLibrary />
        <Middle />
        <RightLinerNotes />
      </div>

      {/* bottom player */}
      <footer className="flex flex-shrink-0 items-center gap-4 px-4 py-3">
        <div className="flex w-[300px] items-center gap-3">
          <span className="flex h-12 w-12 items-center justify-center rounded-md bg-gradient-to-br from-deadly-accent/40 to-deadly-blue/20">⚡</span>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">Fire on the Mountain</p>
            <p className="truncate text-xs text-white/50">Cornell &apos;77 · SBD</p>
          </div>
          <span className="text-deadly-accent">♥</span>
        </div>
        <div className="flex flex-1 flex-col items-center gap-1">
          <div className="flex items-center gap-5 text-white/80">
            <button className="hover:text-white">⏮</button>
            <button className="flex h-9 w-9 items-center justify-center rounded-full bg-white text-black hover:scale-105">⏸</button>
            <button className="hover:text-white">⏭</button>
          </div>
          <div className="flex w-full max-w-md items-center gap-2 text-[11px] text-white/40">
            <span>6:02</span>
            <div className="h-1 flex-1 rounded-full bg-white/15"><div className="h-1 w-2/5 rounded-full bg-white" /></div>
            <span>14:18</span>
          </div>
        </div>
        <div className="flex w-[300px] items-center justify-end gap-3 text-white/50">
          <button className="hover:text-white">🔊</button>
          <div className="h-1 w-24 rounded-full bg-white/15"><div className="h-1 w-2/3 rounded-full bg-white/60" /></div>
        </div>
      </footer>
    </div>
  );
}
