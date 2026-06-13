import type { Metadata, Viewport } from "next";
import AuthProvider from "@/components/auth/AuthProvider";
import UserDataProvider from "@/components/userdata/UserDataProvider";
import ConnectProvider from "@/components/connect/ConnectProvider";
import PlayerProvider from "@/components/player/PlayerProvider";
import ToastProvider from "@/components/ui/ToastProvider";
import NotificationsProvider from "@/components/notifications/NotificationsProvider";
import AppShell from "@/components/shell/AppShell";
import ServiceWorkerRegistrar from "@/components/pwa/ServiceWorkerRegistrar";
import InstallPrompt from "@/components/pwa/InstallPrompt";
import "./globals.css";

export const metadata: Metadata = {
  title: "The Deadly — Every Grateful Dead Concert",
  description:
    "Every Grateful Dead concert — setlists, recordings, and reviews for 2,300+ shows from 1965 to 1995.",
  openGraph: {
    siteName: "The Deadly",
    type: "website",
  },
  // iOS standalone ("Add to Home Screen"). iOS 13 reads these legacy tags
  // rather than the web manifest, so this is what makes it launch full-screen
  // with a dark status bar. Next emits apple-mobile-web-app-{capable,title,
  // status-bar-style}; the apple-touch-icon comes from app/apple-icon.png.
  appleWebApp: {
    capable: true,
    title: "The Deadly",
    statusBarStyle: "black-translucent",
  },
  // Next 16 emits the standardized `mobile-web-app-capable` for appleWebApp.
  // capable, but iOS 13 (our actual target device) only honors the legacy
  // apple-prefixed tag for full-screen standalone launch — so emit it too.
  other: {
    "apple-mobile-web-app-capable": "yes",
  },
};

// Tints the browser/status chrome to match the app background (#121212 =
// --color-deadly-bg). Honored by Android Chrome and the iOS standalone shell.
export const viewport: Viewport = {
  themeColor: "#121212",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-deadly-bg text-white antialiased">
        <ServiceWorkerRegistrar />
        <ToastProvider>
        <AuthProvider>
        <NotificationsProvider>
        <UserDataProvider>
        <ConnectProvider>
        <PlayerProvider>
          <AppShell>{children}</AppShell>
          <InstallPrompt />
        </PlayerProvider>
        </ConnectProvider>
        </UserDataProvider>
        </NotificationsProvider>
        </AuthProvider>
        </ToastProvider>
      </body>
    </html>
  );
}
