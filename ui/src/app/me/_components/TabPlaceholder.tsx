// Shared empty-state for the /me tabs while they're still shells (issue 1
// of PLANS/web-profile.md — layout only, no data wiring yet).
export default function TabPlaceholder({
  title,
  copy,
}: {
  title: string;
  copy: string;
}) {
  return (
    <div className="rounded-lg border border-white/10 bg-deadly-surface p-8 text-center">
      <h2 className="text-lg font-medium text-white">{title}</h2>
      <p className="mx-auto mt-2 max-w-md text-sm text-white/50">{copy}</p>
    </div>
  );
}
