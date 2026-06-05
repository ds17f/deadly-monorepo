"use client";

import { PLATFORMS, usePlatformFilter } from "./PlatformFilterContext";

/**
 * Header chips that scope the entire dashboard to a set of platforms.
 * Defaults to native-only (iOS + Android) so install/active-user counts
 * exclude web browser usage; flip Web on to fold it back in.
 */
export default function PlatformToggles() {
  const { isSelected, toggle } = usePlatformFilter();

  return (
    <div
      className="flex items-center gap-1"
      role="group"
      aria-label="Filter analytics by platform"
    >
      {PLATFORMS.map((p) => {
        const on = isSelected(p.id);
        return (
          <button
            key={p.id}
            type="button"
            onClick={() => toggle(p.id)}
            aria-pressed={on}
            title={
              on
                ? `${p.label} included — click to exclude`
                : `${p.label} excluded — click to include`
            }
            className={`text-xs px-2 py-1 rounded border transition-colors ${
              on
                ? "border-deadly-blue/60 bg-deadly-blue/10 text-deadly-blue"
                : "border-zinc-700 text-zinc-500 hover:bg-zinc-800"
            }`}
          >
            {p.label}
          </button>
        );
      })}
    </div>
  );
}
