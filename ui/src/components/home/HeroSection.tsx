"use client";

export default function HeroSection({ totalShows }: { totalShows: number }) {
  return (
    <header className="mb-8">
      <p className="text-lg font-bold uppercase tracking-wider text-deadly-title">
        The Deadly
      </p>
      <h1 className="mt-1 text-2xl font-bold text-white md:text-3xl">
        A modern player for live Grateful Dead
      </h1>
      <p className="mt-3 text-sm font-semibold text-deadly-heading">
        100% free. 100% open source. No ads, no account required, no paywalls.
      </p>
      <p className="mt-3 max-w-2xl text-sm leading-relaxed text-white/60">
        The Deadly is a{" "}
        <span className="text-deadly-heading">cross-platform music player</span>{" "}
        built around{" "}
        <span className="text-deadly-heading">shows, not recordings</span>.
        All{" "}
        <span className="text-deadly-heading">
          {totalShows.toLocaleString()} known concerts
        </span>{" "}
        from 1965 to 1995 are sourced from the{" "}
        <span className="text-deadly-heading">Internet Archive</span>, but
        instead of digging through tapes you get a clean, show-first
        experience. Our heuristic and AI-powered engine{" "}
        <span className="text-deadly-heading">
          automatically selects the best recording
        </span>{" "}
        for each show, biasing towards{" "}
        <span className="text-deadly-heading">soundboards</span> and highly
        rated sources.
      </p>
      <div className="mt-4 max-w-2xl space-y-3">
        <div>
          <h2 className="text-sm font-bold uppercase tracking-wider text-deadly-title/80">
            Listen anywhere
            <span className="ml-2 inline-block h-px w-16 align-middle bg-white/20" />
          </h2>
          <p className="mt-1 text-sm text-white/60">
            Native{" "}
            <span className="text-deadly-heading">iOS and Android</span> apps
            with background playback,{" "}
            <span className="text-deadly-heading">CarPlay</span>,{" "}
            <span className="text-deadly-heading">Android Auto</span>,
            equalizer, and{" "}
            <span className="text-deadly-heading">show and song favorites</span>.
            Tap play and go.
          </p>
        </div>
        <div>
          <h2 className="text-sm font-bold uppercase tracking-wider text-deadly-title/80">
            Find anything
            <span className="ml-2 inline-block h-px w-16 align-middle bg-white/20" />
          </h2>
          <p className="mt-1 text-sm text-white/60">
            Search across{" "}
            <span className="text-deadly-heading">
              dates, venues, cities, songs, and band members
            </span>
            . Filter by decade, source type, or browse{" "}
            <span className="text-deadly-heading">curated collections</span>.
          </p>
        </div>
        <div>
          <h2 className="text-sm font-bold uppercase tracking-wider text-deadly-title/80">
            Every show reviewed
            <span className="ml-2 inline-block h-px w-16 align-middle bg-white/20" />
          </h2>
          <p className="mt-1 text-sm text-white/60">
            Every show has a{" "}
            <span className="text-deadly-heading">fresh write-up</span> drawn
            from each recording&apos;s listener comments &mdash; covering the
            playing, the highlights, and the quality of each source. Know what
            to listen for before you press play.
          </p>
        </div>
      </div>
    </header>
  );
}
