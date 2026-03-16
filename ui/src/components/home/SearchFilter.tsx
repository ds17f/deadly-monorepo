"use client";

import { useEffect, useRef, useState } from "react";

export type SortBy = "date" | "rating";

export default function SearchFilter({
  searchQuery,
  onSearchChange,
  sortBy,
  onSortChange,
}: {
  searchQuery: string;
  onSearchChange: (q: string) => void;
  sortBy: SortBy;
  onSortChange: (s: SortBy) => void;
}) {
  const [local, setLocal] = useState(searchQuery);
  const timer = useRef<ReturnType<typeof setTimeout>>(null);

  useEffect(() => {
    setLocal(searchQuery);
  }, [searchQuery]);

  function handleChange(value: string) {
    setLocal(value);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => onSearchChange(value), 200);
  }

  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center">
      <div className="relative flex-1">
        <input
          type="text"
          value={local}
          onChange={(e) => handleChange(e.target.value)}
          placeholder="Search shows by venue, city, or date..."
          className="w-full rounded-lg bg-deadly-surface px-4 py-2.5 pr-10 text-sm text-white placeholder-white/30 outline-none ring-1 ring-white/10 focus:ring-deadly-heading"
        />
        {local && (
          <button
            onClick={() => {
              setLocal("");
              onSearchChange("");
            }}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-white/40 hover:text-white"
          >
            {"\u2715"}
          </button>
        )}
      </div>
      <div className="flex gap-1 rounded-lg bg-deadly-surface p-1 ring-1 ring-white/10">
        {(["date", "rating"] as const).map((opt) => (
          <button
            key={opt}
            onClick={() => onSortChange(opt)}
            className={`rounded-md px-3 py-1.5 text-sm capitalize transition-colors ${
              sortBy === opt
                ? "bg-white/15 text-white"
                : "text-white/40 hover:text-white/70"
            }`}
          >
            {opt}
          </button>
        ))}
      </div>
    </div>
  );
}
