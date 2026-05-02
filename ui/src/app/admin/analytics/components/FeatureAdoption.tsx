"use client";

import { useState } from "react";

interface FeatureAdoptionEntry {
  feature: string;
  uses: number;
}

interface FeatureAdoptionData {
  action: FeatureAdoptionEntry[];
  preference: FeatureAdoptionEntry[];
  navigation: FeatureAdoptionEntry[];
  uncategorized: FeatureAdoptionEntry[];
}

interface FeatureAdoptionProps {
  data: FeatureAdoptionData;
  onFeatureClick?: (feature: string) => void;
}

export default function FeatureAdoption({ data, onFeatureClick }: FeatureAdoptionProps) {
  const [showPreferences, setShowPreferences] = useState(false);
  const [showNavigation, setShowNavigation] = useState(false);

  const totalCount =
    data.action.length +
    data.preference.length +
    data.navigation.length +
    data.uncategorized.length;
  if (totalCount === 0) return null;

  return (
    <div className="bg-deadly-surface rounded-lg p-4 space-y-4">
      {data.action.length > 0 && (
        <Section
          label="Actions"
          entries={data.action}
          onFeatureClick={onFeatureClick}
        />
      )}

      {data.uncategorized.length > 0 && (
        <Section
          label="Uncategorized"
          entries={data.uncategorized}
          onFeatureClick={onFeatureClick}
        />
      )}

      {data.preference.length > 0 && (
        <div>
          <button
            onClick={() => setShowPreferences((v) => !v)}
            className="text-xs text-zinc-400 hover:text-zinc-200 transition-colors mb-2"
          >
            {showPreferences ? "▾" : "▸"} Preferences ({data.preference.length})
          </button>
          {showPreferences && (
            <Section entries={data.preference} onFeatureClick={onFeatureClick} />
          )}
        </div>
      )}

      {data.navigation.length > 0 && (
        <div>
          <label className="flex items-center gap-2 text-xs text-zinc-500 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={showNavigation}
              onChange={(e) => setShowNavigation(e.target.checked)}
              className="accent-deadly-accent"
            />
            Show navigation events ({data.navigation.length})
          </label>
          {showNavigation && (
            <div className="mt-2">
              <Section entries={data.navigation} onFeatureClick={onFeatureClick} />
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function Section({
  label,
  entries,
  onFeatureClick,
}: {
  label?: string;
  entries: FeatureAdoptionEntry[];
  onFeatureClick?: (feature: string) => void;
}) {
  const max = Math.max(...entries.map((e) => e.uses), 1);

  return (
    <div className="space-y-2">
      {label && (
        <p className="text-xs uppercase tracking-wider text-zinc-500">{label}</p>
      )}
      {entries.map(({ feature, uses }) => (
        <div
          key={feature}
          className="flex items-center gap-3 cursor-pointer hover:bg-zinc-700/50 rounded px-2 py-1 -mx-2 transition-colors"
          onClick={(e) => {
            if (onFeatureClick) {
              e.stopPropagation();
              onFeatureClick(feature);
            }
          }}
        >
          <span className="text-sm text-zinc-300 w-32 sm:w-40 truncate">{feature}</span>
          <div className="flex-1 bg-zinc-800 rounded-full h-4 overflow-hidden">
            <div
              className="bg-deadly-accent h-full rounded-full transition-all"
              style={{ width: `${(uses / max) * 100}%` }}
            />
          </div>
          <span className="text-sm text-zinc-400 w-10 text-right tabular-nums">{uses}</span>
        </div>
      ))}
    </div>
  );
}
