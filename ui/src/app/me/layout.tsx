"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter, usePathname } from "next/navigation";
import { useEffect } from "react";
import Link from "next/link";
import SyncVersionBanner from "@/components/userdata/SyncVersionBanner";

const TABS = [
  { href: "/me", label: "Profile" },
  { href: "/me/recent", label: "Recent" },
  { href: "/me/favorites", label: "Favorites" },
  { href: "/me/queue", label: "Show Queue" },
  { href: "/me/reviews", label: "Reviews" },
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

  const displayName = user.name ?? "You";
  const initial = displayName.trim()[0]?.toUpperCase() ?? "?";

  return (
    <div>
      {/* Identity header — "Me". Persists across all sub-sections. */}
      <header className="mb-6 flex items-center gap-4">
        {user.image ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={user.image}
            alt=""
            className="h-14 w-14 rounded-full"
            referrerPolicy="no-referrer"
          />
        ) : (
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-deadly-accent text-xl font-bold text-white">
            {initial}
          </span>
        )}
        <div className="min-w-0">
          <h1 className="truncate text-2xl font-bold text-white">
            {displayName}
          </h1>
          {user.email && (
            <p className="truncate text-sm text-white/50">{user.email}</p>
          )}
        </div>
      </header>

      <nav className="mb-8 flex flex-wrap gap-x-6 gap-y-1 border-b border-white/10">
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

      <SyncVersionBanner />

      <div>{children}</div>
    </div>
  );
}
