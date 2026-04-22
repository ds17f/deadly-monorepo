"use client";

interface PlatformChartProps {
  data: Record<string, number>;
  onClick?: () => void;
  onPlatformClick?: (platform: string) => void;
}

const PLATFORM_COLORS: Record<string, string> = {
  ios: "bg-blue-500",
  android: "bg-green-500",
  web: "bg-purple-500",
};

export default function PlatformChart({ data, onClick, onPlatformClick }: PlatformChartProps) {
  const total = Object.values(data).reduce((a, b) => a + b, 0);
  if (total === 0) return null;

  return (
    <section
      className={`mb-6 ${onClick ? "cursor-pointer group" : ""}`}
      onClick={onClick}
    >
      <h2 className="text-sm font-semibold text-zinc-400 uppercase tracking-wider mb-3 group-hover:text-zinc-200 transition-colors">
        Platform Split (30d) {onClick && <span className="text-zinc-600">&rarr;</span>}
      </h2>
      <div className="bg-deadly-surface rounded-lg p-4 space-y-3">
        {Object.entries(data).map(([platform, count]) => (
          <div
            key={platform}
            className="flex items-center gap-3 cursor-pointer hover:bg-zinc-700/50 rounded px-2 py-1 -mx-2 transition-colors"
            onClick={(e) => {
              if (onPlatformClick) {
                e.stopPropagation();
                onPlatformClick(platform);
              }
            }}
          >
            <span className="text-sm text-zinc-300 w-16">{platform}</span>
            <div className="flex-1 bg-zinc-800 rounded-full h-5 overflow-hidden">
              <div
                className={`${PLATFORM_COLORS[platform] ?? "bg-deadly-blue"} h-full rounded-full transition-all`}
                style={{ width: `${(count / total) * 100}%` }}
              />
            </div>
            <span className="text-sm text-zinc-400 w-20 text-right tabular-nums">
              {count} ({Math.round((count / total) * 100)}%)
            </span>
          </div>
        ))}
      </div>
    </section>
  );
}
