import type { AiShowReview, LineupMember } from "@/types/show";
import type { Recording } from "@/types/recording";
import { sourceColors, sourceLabel } from "@/lib/sourceType";

// The "liner notes" right rail on a show page — the editorial content that
// makes The Deadly more than a Spotify clone. Surfaces the structured parts
// of the AI review (highlights, must-listen sequences, per-member band notes,
// the recommended recording) plus the lineup. The long-form prose review
// stays in the middle column ("About this show"); this is the at-a-glance
// companion rail. Renders only the panels it has data for.

function SourceBadge({ type }: { type: string }) {
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${sourceColors(type)}`}>
      {sourceLabel(type)}
    </span>
  );
}

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
  recordings,
  bestRecordingId,
}: {
  showId: string;
  review: AiShowReview | null;
  lineup: LineupMember[] | null;
  recordings: Recording[];
  bestRecordingId: string | null;
}) {
  const highlights = review?.key_highlights ?? [];
  const band = Object.entries(review?.band_performance ?? {}).filter(
    ([, text]) => text && text.length > 0,
  );
  const members = lineup ?? [];

  // The recommended recording, with its source/rating metadata, rendered the
  // way the old in-content "Best Recording" box did (it now lives only here).
  const bestRec =
    recordings.length > 0
      ? recordings.find((r) => r.identifier === bestRecordingId) ??
        recordings.reduce((a, b) => (b.rating > a.rating ? b : a))
      : null;
  const otherCount = recordings.length > 0 ? recordings.length - 1 : 0;
  const reason = review?.best_recording?.reason;

  const hasAnything =
    highlights.length || band.length || bestRec || members.length;
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

      {bestRec && (
        <Panel title="Best recording">
          <div className="flex flex-wrap items-center gap-2">
            <SourceBadge type={bestRec.source_type} />
            {bestRec.rating > 0 && (
              <span className="text-sm text-deadly-star">
                {"★"} {bestRec.rating.toFixed(1)}
              </span>
            )}
            {bestRec.review_count > 0 && (
              <span className="text-xs text-white/50">
                {bestRec.review_count} review
                {bestRec.review_count !== 1 ? "s" : ""}
              </span>
            )}
          </div>
          {reason && (
            <p className="mt-2 text-sm leading-relaxed text-white/60">
              &ldquo;{reason}&rdquo;
            </p>
          )}
          {otherCount > 0 && (
            <p className="mt-2 text-xs text-white/40">
              {otherCount} other recording{otherCount !== 1 ? "s" : ""} available
            </p>
          )}
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
