"use client";

/**
 * The mobile primary-nav bar — the phone counterpart to the desktop
 * LibraryRail (which is `lg:flex`, gone on phones). Without this, mobile users
 * have no way to reach Home / Favorites / Settings except the top-bar
 * search and in-content links; an installed PWA in standalone mode has no
 * browser chrome to fall back on at all.
 *
 * `lg:hidden` — desktop keeps the rail. Sits at the very bottom of the shell,
 * BELOW the player transport (Spotify/Apple-Music ordering: mini-player on top
 * of the tabs). The full-screen "now playing" sheet is z-[60], so it covers
 * the tabs when expanded — same as native.
 *
 * Search lives in the persistent top bar, so it's intentionally not a tab.
 */

import Link from "next/link";
import { usePathname } from "next/navigation";

type Tab = {
  href: string;
  label: string;
  isActive: (path: string) => boolean;
  icon: React.ReactNode;
};

const TABS: Tab[] = [
  {
    href: "/",
    label: "Home",
    isActive: (p) => p === "/",
    icon: (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
        <path d="M12 3 2 12h3v8h6v-5h2v5h6v-8h3z" />
      </svg>
    ),
  },
  {
    href: "/me/favorites",
    label: "Favorites",
    isActive: (p) => p.startsWith("/me/favorites"),
    icon: (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
        <path d="M12 21s-7.5-4.6-10-9.3C.6 8.5 2.2 5 5.5 5c2 0 3.4 1.2 4.5 2.7C11.1 6.2 12.5 5 14.5 5 17.8 5 19.4 8.5 22 11.7 19.5 16.4 12 21 12 21z" />
      </svg>
    ),
  },
  {
    href: "/me/settings",
    label: "Settings",
    isActive: (p) => p.startsWith("/me/settings"),
    icon: (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
        <path d="M19.4 13a7.8 7.8 0 0 0 0-2l2-1.6-2-3.4-2.4 1a7.6 7.6 0 0 0-1.7-1l-.4-2.6h-3.8l-.4 2.6a7.6 7.6 0 0 0-1.7 1l-2.4-1-2 3.4L4.6 11a7.8 7.8 0 0 0 0 2l-2 1.6 2 3.4 2.4-1a7.6 7.6 0 0 0 1.7 1l.4 2.6h3.8l.4-2.6a7.6 7.6 0 0 0 1.7-1l2.4 1 2-3.4zM12 15.5A3.5 3.5 0 1 1 12 8.5a3.5 3.5 0 0 1 0 7z" />
      </svg>
    ),
  },
];

export default function MobileTabBar() {
  const pathname = usePathname();

  return (
    <nav
      aria-label="Primary"
      className="flex flex-shrink-0 items-stretch justify-around border-t border-white/10 bg-deadly-bg lg:hidden"
    >
      {TABS.map((tab) => {
        const active = tab.isActive(pathname);
        return (
          <Link
            key={tab.href}
            href={tab.href}
            aria-current={active ? "page" : undefined}
            className={`flex flex-1 flex-col items-center gap-0.5 py-2 text-[10px] font-medium transition-colors ${
              active ? "text-white" : "text-white/45 hover:text-white/70"
            }`}
          >
            {tab.icon}
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
