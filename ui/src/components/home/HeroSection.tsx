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
        <span className="text-deadly-heading">Internet Archive</span>, which
        lists every tape and leaves you to sort through them. The Deadly{" "}
        <span className="text-deadly-heading">
          automatically picks the best-sounding recording
        </span>{" "}
        for each show, favoring{" "}
        <span className="text-deadly-heading">soundboards</span>{" "}
        and highly rated sources — and you&rsquo;re always free to{" "}
        <span className="text-deadly-heading">switch to a different one</span>{" "}
        you prefer.
      </p>
    </header>
  );
}
