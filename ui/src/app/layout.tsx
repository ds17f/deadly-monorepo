import type { Metadata } from "next";
import AuthProvider from "@/components/auth/AuthProvider";
import UserDataProvider from "@/components/userdata/UserDataProvider";
import ConnectProvider from "@/components/connect/ConnectProvider";
import PlayerProvider from "@/components/player/PlayerProvider";
import ToastProvider from "@/components/ui/ToastProvider";
import AppShell from "@/components/shell/AppShell";
import "./globals.css";

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
        <ToastProvider>
        <AuthProvider>
        <UserDataProvider>
        <ConnectProvider>
        <PlayerProvider>
          <AppShell>{children}</AppShell>
        </PlayerProvider>
        </ConnectProvider>
        </UserDataProvider>
        </AuthProvider>
        </ToastProvider>
      </body>
    </html>
  );
}
