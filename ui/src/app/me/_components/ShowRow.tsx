import Link from "next/link";

// One library row: links to the show page, with a primary line (venue or
// date) and an optional muted secondary line. Shared by Recent + Favorites.
export default function ShowRow({
  showId,
  primary,
  secondary,
}: {
  showId: string;
  primary: string;
  secondary?: string | null;
}) {
  return (
    <Link
      href={`/shows/${showId}`}
      className="flex items-center justify-between gap-4 rounded-lg border border-white/10 bg-deadly-surface px-4 py-3 transition hover:border-white/30"
    >
      <div className="min-w-0">
        <p className="truncate font-medium text-white">{primary}</p>
        {secondary && (
          <p className="mt-0.5 truncate text-xs text-white/40">{secondary}</p>
        )}
      </div>
      <span className="flex-shrink-0 text-sm text-white/40">View show →</span>
    </Link>
  );
}
