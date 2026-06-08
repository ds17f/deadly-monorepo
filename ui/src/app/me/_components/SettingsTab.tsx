"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/contexts/AuthContext";
import { deleteAccount } from "@/lib/userDataApi";
import { SUBREDDIT_HANDLE, SUBREDDIT_URL } from "@/lib/community";

export default function SettingsTab() {
  const { user, signOut } = useAuth();
  const router = useRouter();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmText, setConfirmText] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState(false);

  async function handleDelete() {
    setDeleting(true);
    setError(false);
    try {
      await deleteAccount();
      // Tombstoned server-side; drop the local session and leave /me.
      signOut();
      router.replace("/");
    } catch {
      setError(true);
      setDeleting(false);
    }
  }

  return (
    <div className="space-y-6">
      {/* Account */}
      <section className="rounded-lg border border-white/10 bg-deadly-surface p-5">
        <h3 className="mb-3 font-medium text-white">Account</h3>
        <dl className="space-y-2 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="text-white/40">Email</dt>
            <dd className="truncate text-white/80">{user?.email ?? "—"}</dd>
          </div>
        </dl>
        <p className="mt-2 text-xs text-white/40">
          Edit your display name on the{" "}
          <Link href="/me" className="text-deadly-highlight hover:underline">
            Profile
          </Link>{" "}
          tab.
        </p>
        <button
          onClick={() => signOut()}
          className="mt-4 rounded-md border border-white/15 px-3 py-1.5 text-sm text-white/70 transition hover:border-white/30 hover:text-white"
        >
          Sign out
        </button>
      </section>

      {/* Community */}
      <section className="rounded-lg border border-white/10 bg-deadly-surface p-5">
        <h3 className="mb-2 font-medium text-white">Community</h3>
        <p className="text-sm text-white/50">
          News, tips, and discussion live on our subreddit.
        </p>
        <a
          href={SUBREDDIT_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="mt-3 inline-block rounded-md border border-white/15 px-3 py-1.5 text-sm text-white/70 transition hover:border-white/30 hover:text-white"
        >
          {SUBREDDIT_HANDLE} →
        </a>
      </section>

      {/* Danger zone */}
      <section className="rounded-lg border border-red-500/30 bg-red-500/5 p-5">
        <h3 className="font-medium text-red-300">Delete account</h3>
        <p className="mt-1 text-sm text-white/50">
          Removes your account. Your library is retained but hidden; signing
          in again with the same account reactivates it.
        </p>

        {!confirmOpen ? (
          <button
            onClick={() => setConfirmOpen(true)}
            className="mt-4 rounded-md border border-red-500/40 px-3 py-1.5 text-sm text-red-300 transition hover:bg-red-500/10"
          >
            Delete account…
          </button>
        ) : (
          <div className="mt-4 space-y-3">
            <p className="text-sm text-white/70">
              Type <span className="font-mono font-semibold text-white">DELETE</span>{" "}
              to confirm.
            </p>
            <input
              autoFocus
              value={confirmText}
              onChange={(e) => setConfirmText(e.target.value)}
              placeholder="DELETE"
              className="w-full max-w-xs rounded-md border border-white/15 bg-black/30 px-3 py-2 text-sm text-white placeholder-white/25 focus:border-red-500/50 focus:outline-none"
            />
            {error && (
              <p className="text-sm text-red-400">
                Something went wrong. Please try again.
              </p>
            )}
            <div className="flex gap-2">
              <button
                disabled={confirmText !== "DELETE" || deleting}
                onClick={handleDelete}
                className="rounded-md bg-red-500/80 px-3 py-1.5 text-sm font-medium text-white transition hover:bg-red-500 disabled:cursor-not-allowed disabled:opacity-40"
              >
                {deleting ? "Deleting…" : "Permanently delete"}
              </button>
              <button
                disabled={deleting}
                onClick={() => {
                  setConfirmOpen(false);
                  setConfirmText("");
                  setError(false);
                }}
                className="rounded-md border border-white/15 px-3 py-1.5 text-sm text-white/70 transition hover:border-white/30"
              >
                Cancel
              </button>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
