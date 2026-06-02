import type { AiShowReview, LineupMember } from "@/types/show";

// The "liner notes" right rail on a show page — the editorial content that
// makes The Deadly more than a Spotify clone. Surfaces the structured parts
// of the AI review (highlights, must-listen sequences, per-member band notes,
// the recommended recording) plus the lineup. The long-form prose review
// stays in the middle column ("About this show"); this is the at-a-glance
// companion rail. Renders only the panels it has data for.

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-lg bg-deadly-surface p-4">
      <h3 className="mb-3 text-xs font-bold uppercase tracking-wider text-deadly-title/80">
        {title}
        <span className="ml-2 inline-block h-px w-10 align-middle bg-white/20" />
      </h3>
      {children}
    </section>
  );
}

export default function ShowLinerNotes({
  review,
  lineup,
}: {
  review: AiShowReview | null;
  lineup: LineupMember[] | null;
}) {
  const highlights = review?.key_highlights ?? [];
  const sequences = review?.must_listen_sequences ?? [];
  const band = Object.entries(review?.band_performance ?? {}).filter(
    ([, text]) => text && text.length > 0,
  );
  const bestRec = review?.best_recording;
  const members = lineup ?? [];

  const hasAnything =
    highlights.length ||
    sequences.length ||
    band.length ||
    bestRec ||
    members.length;
  if (!hasAnything) return null;

  return (
    <aside className="flex flex-col gap-3">
      {highlights.length > 0 && (
        <Panel title="Key highlights">
          <ul className="space-y-2 text-sm text-white/70">
            {highlights.map((h) => (
              <li key={h} className="flex gap-2">
                <span className="text-deadly-highlight">&bull;</span>
                {h}
              </li>
            ))}
          </ul>
        </Panel>
      )}

      {sequences.length > 0 && (
        <Panel title="Must-listen sequences">
          <div className="flex flex-col gap-2">
            {sequences.map((seq, i) => (
              <span
                key={i}
                className="rounded-lg bg-white/5 px-3 py-2 text-sm text-white/80"
              >
                {seq.join(" → ")}
              </span>
            ))}
          </div>
        </Panel>
      )}

      {band.length > 0 && (
        <Panel title="Band performance">
          <div className="space-y-2.5">
            {band.map(([member, text]) => (
              <div key={member}>
                <p className="text-sm font-semibold text-white">{member}</p>
                <p className="text-xs text-white/55">{text}</p>
              </div>
            ))}
          </div>
        </Panel>
      )}

      {bestRec && (
        <Panel title="Best recording">
          <p className="break-all text-sm font-semibold text-white">
            {bestRec.identifier}
          </p>
          {bestRec.reason && (
            <p className="mt-1 text-xs text-white/55">{bestRec.reason}</p>
          )}
        </Panel>
      )}

      {members.length > 0 && (
        <Panel title="Lineup">
          <ul className="space-y-1.5">
            {members.map((m) => (
              <li key={m.name} className="text-sm">
                <span className="font-medium text-white">{m.name}</span>
                {m.instruments && (
                  <span className="text-white/45"> — {m.instruments}</span>
                )}
              </li>
            ))}
          </ul>
        </Panel>
      )}
    </aside>
  );
}
