"use client";

interface FeatureAdoptionProps {
  data: Record<string, number>;
  onFeatureClick?: (feature: string) => void;
}

export default function FeatureAdoption({ data, onFeatureClick }: FeatureAdoptionProps) {
  const entries = Object.entries(data);
  if (entries.length === 0) return null;

  const max = Math.max(...entries.map(([, v]) => v), 1);

  return (
    <div className="bg-deadly-surface rounded-lg p-4 space-y-2">
      {entries.map(([feature, count]) => (
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
                style={{ width: `${(count / max) * 100}%` }}
              />
            </div>
            <span className="text-sm text-zinc-400 w-10 text-right tabular-nums">{count}</span>
          </div>
        ))}
    </div>
  );
}
