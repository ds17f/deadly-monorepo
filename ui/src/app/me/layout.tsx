"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter, usePathname } from "next/navigation";
import { useEffect } from "react";
import Link from "next/link";

const TABS = [
  { href: "/me", label: "Recent" },
  { href: "/me/favorites", label: "Favorites" },
  { href: "/me/settings", label: "Settings" },
];

export default function MeLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, isLoading } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!isLoading && !user) {
      router.replace("/signin?callbackUrl=/me");
    }
  }, [isLoading, user, router]);

  // Gate: show nothing meaningful until auth resolves. The effect above
  // redirects signed-out users; until it fires we hold on a loading state
  // rather than flashing the library shell.
  if (isLoading || !user) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <p className="text-sm text-white/40">Loading…</p>
      </div>
    );
  }

  const firstName = user.name?.split(" ")[0] ?? null;

  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-bold text-white">Your library</h1>
        {firstName && (
          <p className="mt-1 text-sm text-white/50">Signed in as {firstName}</p>
        )}
      </header>

      <nav className="mb-8 flex gap-6 border-b border-white/10">
        {TABS.map((tab) => {
          // /me is the index (Recent) — exact match only, otherwise it
          // would light up on every sub-route. Sub-tabs use prefix match.
          const active =
            tab.href === "/me"
              ? pathname === "/me"
              : pathname.startsWith(tab.href);
          return (
            <Link
              key={tab.href}
              href={tab.href}
              className={`-mb-px border-b-2 px-1 pb-3 text-sm font-medium transition ${
                active
                  ? "border-deadly-accent text-white"
                  : "border-transparent text-white/50 hover:text-white"
              }`}
            >
              {tab.label}
            </Link>
          );
        })}
      </nav>

      <div>{children}</div>
    </div>
  );
}
