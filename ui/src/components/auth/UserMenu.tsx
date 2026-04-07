"use client";

import { useState, useRef, useEffect } from "react";
import { useAuth } from "@/contexts/AuthContext";
import DeviceList from "@/components/connect/DeviceList";

export default function UserMenu() {
  const { user, isLoading, signOut } = useAuth();
  const [open, setOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    if (open) {
      document.addEventListener("mousedown", handleClickOutside);
      return () =>
        document.removeEventListener("mousedown", handleClickOutside);
    }
  }, [open]);

  if (isLoading) return null;

  if (!user) {
    const path = typeof window !== "undefined" ? window.location.pathname : "/";
    const callbackUrl = path.startsWith("/api/") ? "/" : path;
    return (
      <a
        href={`/signin?callbackUrl=${encodeURIComponent(callbackUrl)}`}
        className="rounded-md border border-white/20 px-3 py-1.5 text-sm text-white/70 transition hover:border-white/40 hover:text-white"
      >
        Sign in
      </a>
    );
  }

  const firstName = user.name?.split(" ")[0] ?? "User";

  return (
    <div className="relative" ref={menuRef}>
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 rounded-md px-2 py-1 text-sm text-white/80 transition hover:bg-white/10"
      >
        {user.image ? (
          <img
            src={user.image}
            alt=""
            className="h-6 w-6 rounded-full"
            referrerPolicy="no-referrer"
          />
        ) : (
          <span className="flex h-6 w-6 items-center justify-center rounded-full bg-deadly-accent text-xs font-bold text-white">
            {firstName[0].toUpperCase()}
          </span>
        )}
        <span>{firstName}</span>
      </button>

      {open && (
        <div className="absolute right-0 top-full z-50 mt-2 w-56 rounded-lg border border-white/10 bg-deadly-surface p-3 shadow-lg">
          <div className="mb-2 border-b border-white/10 pb-2">
            {user.name && (
              <p className="text-sm font-medium text-white">{user.name}</p>
            )}
            {user.email && (
              <p className="text-xs text-white/50">{user.email}</p>
            )}
          </div>
          <DeviceList />
          <button
            onClick={() => {
              setOpen(false);
              signOut();
            }}
            className="mt-2 w-full rounded-md px-2 py-1.5 text-left text-sm text-white/70 transition hover:bg-white/10 hover:text-white"
          >
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}
