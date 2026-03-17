"use client";

import { useEffect, useRef, useState } from "react";

export type SortBy = "date" | "rating";

const DECADES = [
  { label: "60s", from: 1965, to: 1969 },
  { label: "70s", from: 1970, to: 1979 },
  { label: "80s", from: 1980, to: 1989 },
  { label: "90s", from: 1990, to: 1995 },
] as const;

const SOURCE_TYPES = ["SBD", "AUD", "FM", "Matrix"] as const;

function Chip({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
        active
          ? "bg-deadly-red text-white"
          : "bg-deadly-surface text-white/60 hover:text-white/80"
      }`}
    >
      {label}
    </button>
  );
}

export default function SearchFilter({
  searchQuery,
  onSearchChange,
  sortBy,
  onSortChange,
  selectedDecade,
  onDecadeChange,
  selectedSource,
  onSourceChange,
  includeNoRecordings,
  onIncludeNoRecordingsChange,
}: {
  searchQuery: string;
  onSearchChange: (q: string) => void;
  sortBy: SortBy;
  onSortChange: (s: SortBy) => void;
  selectedDecade: { from: number; to: number } | null;
  onDecadeChange: (d: { from: number; to: number } | null) => void;
  selectedSource: string | null;
  onSourceChange: (s: string | null) => void;
  includeNoRecordings: boolean;
  onIncludeNoRecordingsChange: (v: boolean) => void;
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

  const hasFilters = selectedDecade !== null || selectedSource !== null;

  return (
    <div className="mb-4 space-y-3">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <input
            type="text"
            value={local}
            onChange={(e) => handleChange(e.target.value)}
            placeholder="Search by venue, city, or date..."
            className="w-full rounded-lg bg-deadly-surface px-4 py-2 pr-8 text-sm text-white placeholder-white/30 outline-none ring-1 ring-white/10 focus:ring-deadly-heading"
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
              className={`rounded-md px-3 py-1 text-sm capitalize transition-colors ${
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

      <div className="flex flex-wrap items-center gap-2">
        {DECADES.map((dec) => (
          <Chip
            key={dec.label}
            label={dec.label}
            active={
              selectedDecade?.from === dec.from &&
              selectedDecade?.to === dec.to
            }
            onClick={() =>
              onDecadeChange(
                selectedDecade?.from === dec.from ? null : { from: dec.from, to: dec.to }
              )
            }
          />
        ))}
        <span className="mx-1 h-4 w-px bg-white/15" />
        {SOURCE_TYPES.map((src) => (
          <Chip
            key={src}
            label={src}
            active={selectedSource === src}
            onClick={() => onSourceChange(selectedSource === src ? null : src)}
          />
        ))}
        <span className="mx-1 h-4 w-px bg-white/15" />
        <label className="flex cursor-pointer items-center gap-1.5 text-xs text-white/60">
          <input
            type="checkbox"
            checked={includeNoRecordings}
            onChange={(e) => onIncludeNoRecordingsChange(e.target.checked)}
            className="accent-deadly-red"
          />
          Include shows without recordings
        </label>
        {hasFilters && (
          <>
            <span className="mx-1 h-4 w-px bg-white/15" />
            <button
              onClick={() => {
                onDecadeChange(null);
                onSourceChange(null);
              }}
              className="text-xs text-deadly-heading hover:text-white"
            >
              Clear filters
            </button>
          </>
        )}
      </div>
    </div>
  );
}
