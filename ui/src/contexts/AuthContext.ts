"use client";

import { createContext, useContext } from "react";
import type { AuthUser } from "@/types/auth";

export interface AuthContextValue {
  user: AuthUser | null;
  isLoading: boolean;
  signOut: () => void;
  // Optimistically reflect a display-name change in the current session (the
  // server-side source of truth is accounts.name; this updates the UI now).
  updateName: (name: string) => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
