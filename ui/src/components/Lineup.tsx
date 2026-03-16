import type { LineupMember } from "@/types/show";

export default function Lineup({ members }: { members: LineupMember[] }) {
  return (
    <section className="mt-6 mb-8">
      <h3 className="mb-4 text-lg font-bold text-deadly-title">Lineup</h3>
      <ul className="space-y-1 text-white/80">
        {members.map((m) => (
          <li key={m.name}>
            <span className="font-medium text-white">{m.name}</span>
            <span className="text-white/40"> &mdash; </span>
            <span className="text-sm">{m.instruments}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
