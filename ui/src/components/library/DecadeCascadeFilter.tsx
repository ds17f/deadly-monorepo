"use client";

/**
 * Hierarchical decade→half→year filter, mirroring the native Search screen's
 * cascade (iosApp Core/Design/HierarchicalFilterChips.swift). Tap a node to
 * drill in, tap the selected node to step back; a leaf year collapses to a
 * breadcrumb chip. Chips wrap across rows (no horizontal scroll).
 *
 * Shared by the left LibraryRail and the /me library toolbar so the filter is
 * defined in exactly one place.
 */

export type FilterNode = {
  id: string;
  label: string;
  year?: number;
  children?: FilterNode[];
};

const years = (a: number, b: number): FilterNode[] =>
  Array.from({ length: b - a + 1 }, (_, i) => ({
    id: String(a + i),
    label: String(a + i),
    year: a + i,
  }));

export const FILTER_TREE: FilterNode[] = [
  { id: "60s", label: "60s", children: years(1965, 1969) },
  {
    id: "70s",
    label: "70s",
    children: [
      { id: "early_70s", label: "Early 70s", children: years(1970, 1974) },
      { id: "late_70s", label: "Late 70s", children: years(1975, 1979) },
    ],
  },
  {
    id: "80s",
    label: "80s",
    children: [
      { id: "early_80s", label: "Early 80s", children: years(1980, 1984) },
      { id: "late_80s", label: "Late 80s", children: years(1985, 1989) },
    ],
  },
  { id: "90s", label: "90s", children: years(1990, 1995) },
];

// Every leaf year reachable under a node — the set a selection filters to.
export function leafYears(node: FilterNode): number[] {
  if (node.year != null) return [node.year];
  return (node.children ?? []).flatMap(leafYears);
}

// The years the current path narrows to (null = no filter / "All").
export function selectedYears(path: FilterNode[]): Set<number> | null {
  if (path.length === 0) return null;
  return new Set(leafYears(path[path.length - 1]));
}

// Shows are date-prefixed (YYYY-MM-DD-…); the enriched `date` is preferred.
export function parseYear(show: { showId: string; date?: string | null }): number | null {
  const iso = (show.date ?? show.showId).slice(0, 10);
  const m = /^(\d{4})-\d{2}-\d{2}$/.exec(iso);
  return m ? Number(m[1]) : null;
}

function Chip({
  label,
  active = false,
  onClick,
}: {
  label: string;
  active?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`whitespace-nowrap rounded-full px-3 py-1 text-xs font-medium transition ${
        active
          ? "bg-deadly-accent text-white"
          : "bg-white/10 text-white/80 hover:bg-white/20"
      }`}
    >
      {label}
    </button>
  );
}

export default function DecadeCascadeFilter({
  path,
  onChange,
  className = "",
}: {
  path: FilterNode[];
  onChange: (path: FilterNode[]) => void;
  className?: string;
}) {
  const deepest = path[path.length - 1];

  return (
    <div className={`flex flex-wrap gap-1.5 ${className}`}>
      <Chip label="All" active={path.length === 0} onClick={() => onChange([])} />

      {path.length === 0 ? (
        // Root: the decades.
        FILTER_TREE.map((n) => (
          <Chip key={n.id} label={n.label} onClick={() => onChange([n])} />
        ))
      ) : !deepest.children?.length ? (
        // Leaf year: collapse the whole path into one breadcrumb chip; tapping
        // it steps back up a level.
        <Chip
          active
          label={path.map((n) => n.label).join(" > ")}
          onClick={() => onChange(path.slice(0, -1))}
        />
      ) : (
        // Intermediate: the selected node (tap to step back) + its children.
        <>
          <Chip
            active
            label={deepest.label}
            onClick={() => onChange(path.slice(0, -1))}
          />
          {deepest.children.map((c) => (
            <Chip
              key={c.id}
              label={c.label}
              onClick={() => onChange([...path, c])}
            />
          ))}
        </>
      )}
    </div>
  );
}
