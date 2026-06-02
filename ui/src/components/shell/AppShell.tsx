"use client";

/**
 * The app shell — full-bleed three-pane chrome that replaces the old
 * centered nav/main/footer layout.
 *
 *   top bar  : logo · global search · auth/avatar
 *   left     : persistent LibraryRail (hidden < lg)
 *   middle   : the routed page (children) — scrolls independently
 *   bottom   : transport bar = the EXISTING HeaderPlayer, repositioned
 *              (we do not fork player state — same PlayerProvider)
 *
 * Auth/admin routes render "bare" (just a slim top bar + centered content),
 * since the library rail and transport make no sense there.
 *
 * Static-export safe: client component; per-user panes hydrate from the API.
 */

import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import UserMenu from "@/components/auth/UserMenu";
import HeaderPlayerWrapper from "@/components/player/HeaderPlayerWrapper";
import LibraryRail from "./LibraryRail";

// Routes that should NOT get the full shell (no rail / no transport).
const BARE_PREFIXES = ["/signin", "/auth", "/admin"];

function Logo() {
  return (
    <Link
      href="/"
      className="flex flex-shrink-0 items-center gap-2 text-lg font-bold text-white"
    >
      <Image src="/logo.png" alt="The Deadly logo" width={26} height={26} />
      The Deadly
    </Link>
  );
}

export default function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const bare = BARE_PREFIXES.some((p) => pathname.startsWith(p));

  if (bare) {
    return (
      <div className="min-h-screen bg-deadly-bg">
        <header className="flex items-center justify-between border-b border-white/10 px-6 py-4">
          <Logo />
          <UserMenu />
        </header>
        <main className="mx-auto max-w-5xl px-6 py-8">{children}</main>
      </div>
    );
  }

  return (
    <div className="flex h-screen flex-col bg-black text-white">
      {/* top bar */}
      <header className="flex flex-shrink-0 items-center gap-4 px-4 py-2.5">
        <Logo />
        <div className="mx-auto hidden w-full max-w-md items-center gap-2 rounded-full bg-deadly-surface px-4 py-2 sm:flex">
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="currentColor"
            className="text-white/40"
          >
            <path d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 10-.7.7l.27.28v.79l5 4.99L20.49 19zm-6 0A4.5 4.5 0 1114 9.5 4.5 4.5 0 019.5 14z" />
          </svg>
          {/* Global search is not wired yet — links to the home browse for now. */}
          <Link
            href="/#browse"
            className="w-full truncate text-sm text-white/40 hover:text-white/60"
          >
            Search 2,300+ shows by date, venue, or city
          </Link>
        </div>
        <div className="ml-auto flex-shrink-0">
          <UserMenu />
        </div>
      </header>

      {/* panes */}
      <div className="flex min-h-0 flex-1 gap-2 px-2 pb-2">
        <LibraryRail />
        <main className="min-w-0 flex-1 overflow-y-auto rounded-lg bg-gradient-to-b from-deadly-surface to-deadly-bg">
          <div className="px-4 py-6 sm:px-8">{children}</div>
        </main>
      </div>

      {/* bottom transport — existing HeaderPlayer, do not fork */}
      <div className="flex-shrink-0 border-t border-white/10 bg-deadly-bg px-4 py-2">
        <HeaderPlayerWrapper />
      </div>
    </div>
  );
}
