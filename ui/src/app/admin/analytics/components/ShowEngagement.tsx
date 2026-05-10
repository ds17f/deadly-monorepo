"use client";

interface ShowInfo {
  id: string;
  d: string;
  v: string;
  c: string;
  s: string;
}

export type ActionShowsBucket = "favorited" | "downloaded" | "reviewed" | "shared";

export interface ActionShowsRow {
  show_id: string;
  users: number;
}

export type TopShowsByAction = Record<ActionShowsBucket, ActionShowsRow[]>;

interface Props {
  data: TopShowsByAction;
  showMap: Map<string, ShowInfo>;
  onShowClick?: (showId: string) => void;
}

const SECTION_LABELS: Record<ActionShowsBucket, string> = {
  favorited: "Top favorited",
  downloaded: "Top downloaded",
  reviewed: "Top reviewed",
  shared: "Top shared",
};

function formatDate(d: string): string {
  const date = new Date(d + "T00:00:00");
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

function ShowRow({
  rank,
  row,
  info,
  onShowClick,
}: {
  rank: number;
  row: ActionShowsRow;
  info: ShowInfo | undefined;
  onShowClick?: (showId: string) => void;
}) {
  return (
    <div
      className="bg-deadly-surface rounded-lg p-3 flex items-start gap-3 hover:bg-zinc-700/50 transition-colors cursor-pointer"
      onClick={(e) => {
        if (onShowClick) {
          e.stopPropagation();
          onShowClick(row.show_id);
        }
      }}
    >
      <span className="text-zinc-500 text-sm font-mono w-5 text-right flex-shrink-0 pt-0.5">
        {rank}
      </span>
      <div className="min-w-0 flex-1">
        {info ? (
          <>
            <p className="text-sm text-zinc-200">{formatDate(info.d)}</p>
            <p className="text-xs text-zinc-400 truncate">
              {info.v} &mdash; {info.c}, {info.s}
            </p>
          </>
        ) : (
          <p className="text-sm text-zinc-300 font-mono">{row.show_id}</p>
        )}
      </div>
      <span className="text-sm text-zinc-400 flex-shrink-0 tabular-nums">
        {row.users} user{row.users !== 1 ? "s" : ""}
      </span>
    </div>
  );
}

function Section({
  label,
  rows,
  showMap,
  onShowClick,
}: {
  label: string;
  rows: ActionShowsRow[];
  showMap: Map<string, ShowInfo>;
  onShowClick?: (showId: string) => void;
}) {
  return (
    <div>
      <p className="text-xs uppercase tracking-wider text-zinc-500 mb-2">
        {label}
      </p>
      {rows.length === 0 ? (
        <p className="text-xs text-zinc-600 italic">No data</p>
      ) : (
        <div className="space-y-1">
          {rows.map((row, i) => (
            <ShowRow
              key={row.show_id}
              rank={i + 1}
              row={row}
              info={showMap.get(row.show_id)}
              onShowClick={onShowClick}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default function ShowEngagement({ data, showMap, onShowClick }: Props) {
  const buckets: ActionShowsBucket[] = ["favorited", "downloaded", "reviewed", "shared"];
  const safe = data ?? ({} as Partial<TopShowsByAction>);
  const total = buckets.reduce((acc, b) => acc + (safe[b]?.length ?? 0), 0);
  if (total === 0) {
    return (
      <p className="text-sm text-zinc-500 italic">
        No show-level engagement events yet. Once apps with target_id-tagged
        feature_use events ship, this section will populate.
      </p>
    );
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      {buckets.map((b) => (
        <Section
          key={b}
          label={SECTION_LABELS[b]}
          rows={safe[b] ?? []}
          showMap={showMap}
          onShowClick={onShowClick}
        />
      ))}
    </div>
  );
}
