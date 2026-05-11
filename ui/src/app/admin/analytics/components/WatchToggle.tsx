"use client";

import { useEffect, useRef, useState } from "react";
import { useWatchedInstalls } from "./WatchedInstallsContext";

export default function WatchToggle({ iid }: { iid: string }) {
  const { isWatched, nameFor, setWatched, unwatch } = useWatchedInstalls();
  const watched = isWatched(iid);
  const currentName = nameFor(iid);
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(currentName ?? "");
  const inputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    setName(currentName ?? "");
  }, [currentName]);

  useEffect(() => {
    if (editing) inputRef.current?.focus();
  }, [editing]);

  const handleToggle = async () => {
    if (watched) {
      await unwatch(iid);
      setEditing(false);
    } else {
      // First watch: flag it, then open the name editor as a soft prompt.
      await setWatched(iid, null, null);
      setEditing(true);
    }
  };

  const handleSave = async () => {
    const trimmed = name.trim();
    await setWatched(iid, trimmed || null, null);
    setEditing(false);
  };

  return (
    <div className="flex items-center gap-2 flex-wrap">
      <button
        onClick={handleToggle}
        className={`text-sm flex items-center gap-1 px-2 py-1 rounded border transition-colors ${
          watched
            ? "border-amber-500/60 bg-amber-500/10 text-amber-300 hover:bg-amber-500/20"
            : "border-zinc-700 text-zinc-300 hover:bg-zinc-800"
        }`}
        title={watched ? "Stop watching this install" : "Watch this install"}
      >
        <span>{watched ? "★" : "☆"}</span>
        <span>{watched ? "Watching" : "Watch"}</span>
      </button>

      {watched && !editing && (
        <button
          onClick={() => setEditing(true)}
          className="text-xs text-zinc-400 hover:text-white"
        >
          {currentName ? `“${currentName}” — rename` : "+ add name"}
        </button>
      )}

      {watched && editing && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleSave();
          }}
          className="flex items-center gap-1"
        >
          <input
            ref={inputRef}
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="friendly name (optional)"
            className="bg-zinc-800 border border-zinc-700 rounded px-2 py-1 text-sm text-white placeholder:text-zinc-500 w-48"
            onKeyDown={(e) => {
              if (e.key === "Escape") {
                e.preventDefault();
                setName(currentName ?? "");
                setEditing(false);
              }
            }}
          />
          <button
            type="submit"
            className="text-xs text-zinc-300 hover:text-white px-2 py-1 rounded border border-zinc-700 bg-zinc-800"
          >
            save
          </button>
          <button
            type="button"
            onClick={() => {
              setName(currentName ?? "");
              setEditing(false);
            }}
            className="text-xs text-zinc-500 hover:text-zinc-300 px-1"
          >
            cancel
          </button>
        </form>
      )}
    </div>
  );
}
