"use client";

import { useState, useEffect, useCallback } from "react";
import type { AuthUser } from "@/types/auth";
import { AuthContext } from "@/contexts/AuthContext";

export default function AuthProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    fetch("/api/auth/session", { credentials: "include" })
      .then((res) => res.json())
      .then((data) => {
        setUser(data?.user ?? null);
      })
      .catch(() => {
        setUser(null);
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, []);

  const signOut = useCallback(() => {
    fetch("/api/auth/csrf", { credentials: "include" })
      .then((res) => res.json())
      .then((data) => {
        const body = new URLSearchParams();
        body.append("csrfToken", data.csrfToken);
        return fetch("/api/auth/signout", {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body,
        });
      })
      .then(() => {
        setUser(null);
      })
      .catch(() => {
        // Sign out failed silently — user remains signed in
      });
  }, []);

  return (
    <AuthContext.Provider value={{ user, isLoading, signOut }}>
      {children}
    </AuthContext.Provider>
  );
}
