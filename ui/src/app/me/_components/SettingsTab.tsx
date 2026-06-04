"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/contexts/AuthContext";
import { deleteAccount, updateDisplayName } from "@/lib/userDataApi";

export default function SettingsTab() {
  const { user, signOut, updateName } = useAuth();
  const router = useRouter();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmText, setConfirmText] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState(false);

  // Display-name editing.
  const [editingName, setEditingName] = useState(false);
  const [nameInput, setNameInput] = useState("");
  const [savingName, setSavingName] = useState(false);
  const [nameError, setNameError] = useState<string | null>(null);

  function startEditName() {
    setNameInput(user?.name ?? "");
    setNameError(null);
    setEditingName(true);
  }

  async function saveName() {
    const trimmed = nameInput.trim();
    if (trimmed.length < 1 || trimmed.length > 60) {
      setNameError("Name must be 1–60 characters.");
      return;
    }
    if (trimmed === user?.name) {
      setEditingName(false);
      return;
    }
    setSavingName(true);
    setNameError(null);
    try {
      const { name } = await updateDisplayName(trimmed);
      updateName(name); // reflect now; persists via accounts.name on reload
      setEditingName(false);
    } catch {
      setNameError("Couldn’t save. Try again.");
    } finally {
      setSavingName(false);
    }
  }

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
          <div className="flex items-center justify-between gap-4">
            <dt className="flex-shrink-0 text-white/40">Name</dt>
            {editingName ? (
              <dd className="flex flex-1 flex-col items-end gap-2">
                <input
                  autoFocus
                  value={nameInput}
                  onChange={(e) => setNameInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") saveName();
                    if (e.key === "Escape") setEditingName(false);
                  }}
                  maxLength={60}
                  className="w-full max-w-xs rounded-md border border-white/15 bg-black/30 px-3 py-1.5 text-right text-white focus:border-white/40 focus:outline-none"
                />
                <div className="flex items-center gap-2">
                  {nameError && <span className="text-xs text-red-400">{nameError}</span>}
                  <button
                    disabled={savingName}
                    onClick={saveName}
                    className="rounded-md bg-white px-3 py-1 text-xs font-semibold text-black transition hover:opacity-90 disabled:opacity-40"
                  >
                    {savingName ? "Saving…" : "Save"}
                  </button>
                  <button
                    disabled={savingName}
                    onClick={() => setEditingName(false)}
                    className="rounded-md border border-white/15 px-3 py-1 text-xs text-white/70 transition hover:border-white/30"
                  >
                    Cancel
                  </button>
                </div>
              </dd>
            ) : (
              <dd className="flex min-w-0 items-center gap-2">
                <span className="truncate text-white/80">{user?.name ?? "—"}</span>
                <button
                  onClick={startEditName}
                  className="flex-shrink-0 text-xs text-deadly-highlight transition hover:underline"
                >
                  Edit
                </button>
              </dd>
            )}
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-white/40">Email</dt>
            <dd className="truncate text-white/80">{user?.email ?? "—"}</dd>
          </div>
        </dl>
        <button
          onClick={() => signOut()}
          className="mt-4 rounded-md border border-white/15 px-3 py-1.5 text-sm text-white/70 transition hover:border-white/30 hover:text-white"
        >
          Sign out
        </button>
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
