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
import { useEffect, useRef } from "react";
import { usePathname } from "next/navigation";
import * as analytics from "@/lib/analytics";
import UserMenu from "@/components/auth/UserMenu";
import HeaderPlayerWrapper from "@/components/player/HeaderPlayerWrapper";
import LibraryRail from "./LibraryRail";
import MobileTabBar from "./MobileTabBar";
import MobileBackButton from "./MobileBackButton";
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
      {/* Wordmark hidden on phones so the search box has room in the top bar. */}
      <span className="hidden sm:inline">The Deadly</span>
    </Link>
  );
}

function ShellChrome({ children }: { children: React.ReactNode }) {
  const rightNode = useRightRailNode();
  const placement = useRightRailPlacement();
  const pathname = usePathname();

  // Mobile = the panes container is the lone scroll region (the document is
  // locked). Client navigation can't reset an inner scroller, so arriving at a
  // show from a scrolled-down home could land mid-page (only "‹ ... ›" in view).
  // Reset it to the top on every route change.
  const panesRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    panesRef.current?.scrollTo({ top: 0 });
  }, [pathname]);

  return (
    // `fixed` (not just an in-flow h-[100dvh] div): pins the shell to the
    // viewport so the header/transport can never scroll away. An in-flow
    // column let mobile browsers scroll the whole shell under the URL bar
    // on navigation, dropping the top nav. top-0 + h-[100dvh] keeps the bar
    // pinned while the height still tracks the dynamic (URL-bar) viewport.
    <div className="fixed inset-x-0 top-0 flex h-[100dvh] flex-col overflow-hidden bg-black text-white">
      {/* top bar: equal-flex sides keep the search box viewport-centered */}
      <header className="flex flex-shrink-0 items-center gap-4 px-4 py-2.5">
        <div className="flex items-center sm:flex-1">
          <MobileBackButton />
          <Logo />
        </div>
        <SearchBox />
        <div className="flex items-center justify-end sm:flex-1">
          <UserMenu />
        </div>
      </header>

      {/* panes: row on desktop (each pane scrolls internally), stacked into
          ONE scroll region on mobile. Either way the document never scrolls,
          so the header + transport stay fixed and wheeling over them moves
          nothing. */}
      <div
        ref={panesRef}
        className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto overflow-x-hidden overscroll-contain px-2 pb-2 lg:flex-row lg:overflow-hidden"
      >
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

      {/* mobile primary nav — sits below the transport, hidden on desktop
          (the LibraryRail covers nav there). */}
      <MobileTabBar />
    </div>
  );
}

export default function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const bare = BARE_PREFIXES.some((p) => pathname.startsWith(p));

  // One app_open per page-load session — the web's "session" signal, feeding
  // DAU / installs by platform alongside iOS/Android.
  useEffect(() => {
    analytics.track("app_open");
  }, []);

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
