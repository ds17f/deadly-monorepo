"use client";

/**
 * The app shell — full-bleed three-pane chrome that replaces the old
 * centered nav/main/footer layout.
 *
 *   top bar  : logo · global search · auth/avatar
 *   left     : persistent LibraryRail
 *   middle   : the routed page (children)
 *   right    : route-driven slot (RightRail) — liner notes on a show, etc.
 *   bottom   : transport bar = the EXISTING HeaderPlayer (same
 *              PlayerProvider — not forked)
 *
 * Desktop (lg+): locked to the viewport height; each pane scrolls
 * independently. Mobile: panes stack into one natural page scroll — the
 * library rail collapses away, the right-pane content (e.g. liner notes)
 * flows below the page content, and the transport sticks to the bottom.
 *
 * Auth/admin routes render "bare" (slim top bar + centered content).
 * Static-export safe: client component; per-user panes hydrate from the API.
 */

import Link from "next/link";
import Image from "next/image";
import { useEffect } from "react";
import { usePathname } from "next/navigation";
import UserMenu from "@/components/auth/UserMenu";
import HeaderPlayerWrapper from "@/components/player/HeaderPlayerWrapper";
import LibraryRail from "./LibraryRail";
import SearchBox from "./SearchBox";
import { RightRailProvider, useRightRailNode, useRightRailPlacement } from "./RightRail";

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

function ShellChrome({ children }: { children: React.ReactNode }) {
  const rightNode = useRightRailNode();
  const placement = useRightRailPlacement();

  return (
    <div className="flex h-[100dvh] flex-col bg-black text-white">
      {/* top bar: equal-flex sides keep the search box viewport-centered */}
      <header className="flex flex-shrink-0 items-center gap-4 px-4 py-2.5">
        <div className="flex flex-1 items-center">
          <Logo />
        </div>
        <SearchBox />
        <div className="flex flex-1 items-center justify-end">
          <UserMenu />
        </div>
      </header>

      {/* panes: row on desktop (each pane scrolls internally), stacked into
          ONE scroll region on mobile. Either way the document never scrolls,
          so the header + transport stay fixed and wheeling over them moves
          nothing. */}
      <div className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto px-2 pb-2 lg:flex-row lg:overflow-hidden">
        <LibraryRail />
        <main className="min-w-0 rounded-lg bg-gradient-to-b from-deadly-surface to-deadly-bg lg:min-h-0 lg:flex-1 lg:overflow-y-auto">
          <div className="px-4 py-6 sm:px-8">{children}</div>
        </main>
        {rightNode != null && (
          <div
            className={`w-full flex-shrink-0 lg:order-none lg:max-h-full lg:w-[360px] lg:min-h-0 lg:overflow-y-auto ${
              placement === "above" ? "order-first" : ""
            }`}
          >
            {rightNode}
          </div>
        )}
      </div>

      {/* bottom transport — existing HeaderPlayer, do not fork. A fixed flex
          item outside the scroll region, so it's pinned without `sticky`. */}
      <div className="flex-shrink-0 border-t border-white/10 bg-deadly-bg px-4 py-2">
        <HeaderPlayerWrapper />
      </div>
    </div>
  );
}

export default function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const bare = BARE_PREFIXES.some((p) => pathname.startsWith(p));

  // The shell owns a fixed h-[100dvh] viewport — the document must NOT scroll,
  // or wheeling over the pinned header/transport pages it (the body is
  // min-h-screen). Bare routes keep normal document scroll.
  useEffect(() => {
    if (bare) return;
    const html = document.documentElement;
    const prev = html.style.overflow;
    html.style.overflow = "hidden";
    return () => {
      html.style.overflow = prev;
    };
  }, [bare]);

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
    <RightRailProvider>
      <ShellChrome>{children}</ShellChrome>
    </RightRailProvider>
  );
}
