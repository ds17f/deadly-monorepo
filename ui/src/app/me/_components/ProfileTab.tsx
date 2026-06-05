"use client";

// Profile landing for /me — the person: identity + social. The display name
// is editable here (it's identity); profile picture and the social surfaces
// (friends, listening privacy) have no backend yet and stay honest "coming
// soon" placeholders. See PLANS/web-profile.md (issue 1b).

import { useRef, useState } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { updateDisplayName, uploadAvatar, deleteAvatar } from "@/lib/userDataApi";
import { downscaleToAvatar } from "@/lib/avatar";

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

function AvatarCard() {
  const { user, updateImage } = useAuth();
  const inputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const initial = (user?.name?.trim()?.[0] ?? "?").toUpperCase();

  async function onPick(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = ""; // allow re-selecting the same file
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      const blob = await downscaleToAvatar(file);
      const { image } = await uploadAvatar(blob);
      // Bust the immutable cache so the new picture shows immediately; the
      // session's versioned URL takes over on the next refresh.
      updateImage(`${image}?v=${Date.now()}`);
    } catch {
      setError("Couldn’t upload that image. Try a different one.");
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    setBusy(true);
    setError(null);
    try {
      await deleteAvatar();
      updateImage(null);
    } catch {
      setError("Couldn’t remove the picture. Try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="rounded-lg border border-white/10 bg-deadly-surface p-5">
      <h3 className="font-medium text-white">Profile picture</h3>
      <p className="mt-1.5 text-sm text-white/50">
        The picture other Deadheads will see next to your name.
      </p>

      <div className="mt-3 flex items-center gap-4">
        {user?.image ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={user.image}
            alt=""
            className="h-16 w-16 flex-shrink-0 rounded-full object-cover"
            referrerPolicy="no-referrer"
          />
        ) : (
          <span className="flex h-16 w-16 flex-shrink-0 items-center justify-center rounded-full bg-deadly-accent text-xl font-bold text-white">
            {initial}
          </span>
        )}

        <div className="flex flex-wrap items-center gap-2">
          <input
            ref={inputRef}
            type="file"
            accept="image/*"
            className="hidden"
            onChange={onPick}
          />
          <button
            disabled={busy}
            onClick={() => inputRef.current?.click()}
            className="rounded-md bg-white px-3 py-1.5 text-sm font-semibold text-black transition hover:opacity-90 disabled:opacity-40"
          >
            {busy ? "Uploading…" : user?.image ? "Change" : "Upload"}
          </button>
          {user?.image && (
            <button
              disabled={busy}
              onClick={remove}
              className="rounded-md border border-white/15 px-3 py-1.5 text-sm text-white/70 transition hover:border-white/30 disabled:opacity-40"
            >
              Remove
            </button>
          )}
        </div>
      </div>

      {error && <p className="mt-2 text-xs text-red-400">{error}</p>}
    </div>
  );
}

export default function ProfileTab() {
  return (
    <section className="space-y-3">
      <DisplayNameCard />
      <AvatarCard />
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
