import type { Recording } from "@/types/recording";

const SOURCE_COLORS: Record<string, string> = {
  SBD: "bg-deadly-highlight text-white",
  FM: "bg-deadly-highlight text-white",
  Matrix: "bg-deadly-highlight text-white",
  Remaster: "bg-deadly-highlight text-white",
  AUD: "bg-amber-700 text-white",
  UNKNOWN: "bg-white/20 text-white/70",
};

interface RecordingSelectorProps {
  recordings: Recording[];
  selectedId: string | null;
  onSelect: (identifier: string) => void;
}

export default function RecordingSelector({
  recordings,
  selectedId,
  onSelect,
}: RecordingSelectorProps) {
  if (recordings.length <= 1) return null;

  return (
    <div className="mt-4">
      <h4 className="mb-2 text-sm font-bold text-deadly-title">Recordings</h4>
      <div className="space-y-1.5">
        {recordings.map((rec) => {
          const isSelected = rec.identifier === selectedId;
          const sourceLabel =
            rec.source_type === "UNKNOWN" ? "Unknown" : rec.source_type;
          const colors =
            SOURCE_COLORS[rec.source_type] ?? SOURCE_COLORS.UNKNOWN;

          return (
            <button
              key={rec.identifier}
              onClick={() => onSelect(rec.identifier)}
              className={`flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left transition-colors hover:bg-white/5 ${
                isSelected
                  ? "border border-deadly-highlight/30 bg-white/5"
                  : "border border-transparent"
              }`}
            >
              <span
                className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${colors}`}
              >
                {sourceLabel}
              </span>
              {rec.rating > 0 && (
                <span className="text-xs text-deadly-star">
                  {"\u2605"} {rec.rating.toFixed(1)}
                </span>
              )}
              {rec.review_count > 0 && (
                <span className="text-xs text-white/40">
                  ({rec.review_count})
                </span>
              )}
              {rec.runtime && (
                <span className="ml-auto text-xs text-white/30">
                  {rec.runtime}
                </span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
