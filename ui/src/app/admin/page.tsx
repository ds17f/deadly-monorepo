"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import Link from "next/link";

const adminPages = [
  {
    href: "/admin/catalog",
    title: "Catalog",
    description: "Artists, shows, recordings, and import pipeline",
  },
  {
    href: "/admin/analytics",
    title: "Analytics",
    description: "Usage metrics, active users, feature adoption",
  },
];

export default function AdminIndex() {
  const { user, isLoading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !user?.isAdmin) {
      router.replace("/");
    }
  }, [isLoading, user?.isAdmin, router]);

  if (isLoading || !user?.isAdmin) {
    return (
      <div className="min-h-screen bg-deadly-bg flex items-center justify-center">
        <p className="text-zinc-400">Loading...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-deadly-bg p-6 max-w-5xl mx-auto">
      <h1 className="text-2xl font-bold text-deadly-red mb-8">Admin</h1>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {adminPages.map((page) => (
          <Link
            key={page.href}
            href={page.href}
            className="bg-deadly-surface rounded-lg p-6 hover:ring-1 hover:ring-zinc-600 transition-all"
          >
            <h2 className="text-lg font-semibold text-white mb-1">{page.title}</h2>
            <p className="text-sm text-zinc-400">{page.description}</p>
          </Link>
        ))}
      </div>
    </div>
  );
}
