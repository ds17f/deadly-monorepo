"use client";

import { useAuth } from "@/contexts/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

const ADMIN_PAGES = [
  { href: "/admin/analytics", title: "Analytics", description: "Usage metrics, installs, and feature adoption" },
  { href: "/admin/beta", title: "Beta", description: "Manage TestFlight beta applicants and invitations" },
];

export default function AdminPage() {
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
    <div className="min-h-screen bg-deadly-bg text-zinc-100 p-6">
      <div className="max-w-2xl mx-auto">
        <h1 className="text-2xl font-bold text-deadly-red mb-8">Admin</h1>
        <div className="space-y-3">
          {ADMIN_PAGES.map((page) => (
            <a
              key={page.href}
              href={page.href}
              className="block bg-deadly-surface border border-zinc-800 rounded-lg p-4 hover:border-zinc-600 transition-colors"
            >
              <h2 className="font-medium text-zinc-100">{page.title}</h2>
              <p className="text-sm text-zinc-400 mt-1">{page.description}</p>
            </a>
          ))}
        </div>
      </div>
    </div>
  );
}
