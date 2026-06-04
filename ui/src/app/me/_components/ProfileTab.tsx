"use client";

// Profile landing for /me — the person: identity + social. The display name
// is editable here (it's identity); profile picture and the social surfaces
// (friends, listening privacy) have no backend yet and stay honest "coming
// soon" placeholders. See PLANS/web-profile.md (issue 1b).

import { useState } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { updateDisplayName } from "@/lib/userDataApi";

function ComingSoon({ title, copy }: { title: string; copy: string }) {
  return (
    <div className="rounded-lg border border-white/10 bg-deadly-surface p-5">
      <div className="flex items-center justify-between gap-3">
        <h3 className="font-medium text-white">{title}</h3>
        <span className="flex-shrink-0 rounded-full border border-white/15 px-2 py-0.5 text-[11px] uppercase tracking-wide text-white/40">
          Coming soon
        </span>
      </div>
      <p className="mt-1.5 text-sm text-white/50">{copy}</p>
    </div>
  );
}

function DisplayNameCard() {
  const { user, updateName } = useAuth();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function start() {
    setValue(user?.name ?? "");
    setError(null);
    setEditing(true);
  }

  async function save() {
    const trimmed = value.trim();
    if (trimmed.length < 1 || trimmed.length > 60) {
      setError("Name must be 1–60 characters.");
      return;
    }
    if (trimmed === user?.name) {
      setEditing(false);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const { name } = await updateDisplayName(trimmed);
      updateName(name); // reflect now; persists via accounts.name on reload
      setEditing(false);
    } catch {
      setError("Couldn’t save. Try again.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="rounded-lg border border-white/10 bg-deadly-surface p-5">
      <h3 className="font-medium text-white">Display name</h3>
      <p className="mt-1.5 text-sm text-white/50">
        The name other Deadheads will see.
      </p>

      {editing ? (
        <div className="mt-3 space-y-2">
          <input
            autoFocus
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") save();
              if (e.key === "Escape") setEditing(false);
            }}
            maxLength={60}
            className="w-full max-w-sm rounded-md border border-white/15 bg-black/30 px-3 py-2 text-sm text-white focus:border-white/40 focus:outline-none"
          />
          <div className="flex items-center gap-2">
            <button
              disabled={saving}
              onClick={save}
              className="rounded-md bg-white px-3 py-1.5 text-sm font-semibold text-black transition hover:opacity-90 disabled:opacity-40"
            >
              {saving ? "Saving…" : "Save"}
            </button>
            <button
              disabled={saving}
              onClick={() => setEditing(false)}
              className="rounded-md border border-white/15 px-3 py-1.5 text-sm text-white/70 transition hover:border-white/30"
            >
              Cancel
            </button>
            {error && <span className="text-xs text-red-400">{error}</span>}
          </div>
        </div>
      ) : (
        <div className="mt-3 flex items-center gap-3">
          <span className="truncate text-white/80">{user?.name ?? "—"}</span>
          <button
            onClick={start}
            className="flex-shrink-0 text-sm text-deadly-highlight transition hover:underline"
          >
            Edit
          </button>
        </div>
      )}
    </div>
  );
}

export default function ProfileTab() {
  return (
    <section className="space-y-3">
      <DisplayNameCard />
      <ComingSoon
        title="Profile picture"
        copy="Upload a picture other Deadheads will see next to your name."
      />
      <ComingSoon
        title="Friends & contacts"
        copy="Add and remove friends to follow what they're listening to."
      />
      <ComingSoon
        title="Listening privacy"
        copy="Control who can see — and hear — what you're playing right now."
      />
    </section>
  );
}
