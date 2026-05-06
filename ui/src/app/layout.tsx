import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";
import AppStoreBadge from "@/components/AppStoreBadge";
import AuthProvider from "@/components/auth/AuthProvider";
import UserMenu from "@/components/auth/UserMenu";
import UserDataProvider from "@/components/userdata/UserDataProvider";
import ConnectProvider from "@/components/connect/ConnectProvider";
import PlayerProvider from "@/components/player/PlayerProvider";
import HeaderPlayerWrapper from "@/components/player/HeaderPlayerWrapper";
import "./globals.css";

const PLAY_STORE_URL =
  "https://play.google.com/store/apps/details?id=com.grateful.deadly";
const GOOGLE_PLAY_BADGE_URL =
  "https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png";

export const metadata: Metadata = {
  title: "The Deadly — Every Grateful Dead Concert",
  description:
    "Every Grateful Dead concert — setlists, recordings, and reviews for 2,300+ shows from 1965 to 1995.",
  openGraph: {
    siteName: "The Deadly",
    type: "website",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-deadly-bg text-white antialiased">
        <AuthProvider>
        <UserDataProvider>
        <ConnectProvider>
        <PlayerProvider>
          <nav className="border-b border-white/10 px-6 py-4">
            <div className="mx-auto flex max-w-5xl items-center justify-between">
              <Link
                href="/"
                className="flex items-center gap-2 text-xl font-bold text-white"
              >
                <Image
                  src="/logo.png"
                  alt="The Deadly logo"
                  width={28}
                  height={28}
                />
                The Deadly
              </Link>
              <div className="flex items-center gap-4">
                <HeaderPlayerWrapper />
                <UserMenu />
              </div>
            </div>
          </nav>
          <main className="mx-auto max-w-5xl px-6 py-8">{children}</main>
          <footer className="border-t border-white/10 px-6 py-6">
            <div className="mx-auto flex max-w-5xl flex-col items-center gap-4 text-sm text-white/30">
              <div className="flex items-center gap-3">
                <a
                  href={PLAY_STORE_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <Image
                    src={GOOGLE_PLAY_BADGE_URL}
                    alt="Get it on Google Play"
                    width={140}
                    height={42}
                    unoptimized
                  />
                </a>
                <AppStoreBadge />
              </div>
              <div className="flex flex-wrap justify-center gap-x-3 gap-y-1">
                <Link
                  href="/privacy"
                  className="text-white/40 hover:text-white/70"
                >
                  Privacy Policy
                </Link>
                <span>&middot;</span>
                <a
                  href="https://github.com/ds17f/deadly-monorepo"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-white/40 hover:text-white/70"
                >
                  Open source on GitHub
                </a>
                <span>&middot;</span>
                <span>
                  Recordings courtesy of the{" "}
                  <a
                    href="https://archive.org/details/GratefulDead"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-white/40 hover:text-white/70"
                  >
                    Internet Archive
                  </a>
                </span>
              </div>
            </div>
          </footer>
        </PlayerProvider>
        </ConnectProvider>
        </UserDataProvider>
        </AuthProvider>
      </body>
    </html>
  );
}
