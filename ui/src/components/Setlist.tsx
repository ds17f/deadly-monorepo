import type { SetlistSet } from "@/types/show";

export default function Setlist({
  sets,
  songHighlights,
}: {
  sets: SetlistSet[];
  songHighlights?: string[];
}) {
  const highlights = new Set(
    (songHighlights ?? []).map((s) => s.toLowerCase())
  );

  return (
    <section className="mt-6 mb-8">
      <h3 className="mb-4 text-lg font-bold text-deadly-title">Setlist</h3>
      {sets.map((set) => (
        <div key={set.set_name} className="mb-4">
          <h4 className="mb-2 text-sm font-bold uppercase tracking-wider text-deadly-title/80">
            {set.set_name}
            <span className="ml-2 inline-block h-px w-16 align-middle bg-white/20" />
          </h4>
          <div className="text-white/90">
            {set.songs.map((song, i) => {
              const isHighlight = highlights.has(song.name.toLowerCase());
              return (
                <span key={`${song.name}-${i}`}>
                  {isHighlight ? (
                    <span className="rounded bg-deadly-highlight/10 px-1 py-0.5 text-deadly-highlight">
                      <span className="whitespace-nowrap">{"\uD83D\uDD25"}{" "}{song.name.split(" ")[0]}</span>{song.name.includes(" ") ? " " + song.name.split(" ").slice(1).join(" ") : ""}
                    </span>
                  ) : (
                    song.name
                  )}
                  {song.segue_into_next && (
                    <span className="mx-1 text-white/40">&gt;</span>
                  )}
                  {!song.segue_into_next && i < set.songs.length - 1 && ", "}
                </span>
              );
            })}
          </div>
        </div>
      ))}
    </section>
  );
}
