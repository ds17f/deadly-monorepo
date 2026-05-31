// Profile landing for /me. The identity (avatar + name) lives in the shared
// header; this surface is where profile + social management will go. None
// of it has a backend yet, so each block is an honest "coming soon"
// placeholder. See PLANS/web-profile.md (issues 1b) — friend graph,
// presence, and listening-privacy are a future design + API effort.

function ComingSoon({ title, copy }: { title: string; copy: string }) {
  return (
    <div className="rounded-lg border border-white/10 bg-deadly-surface p-5">
      <div className="flex items-center justify-between gap-3">
        <h3 className="font-medium text-white">{title}</h3>
        <span className="flex-shrink-0 rounded-full border border-white/15 px-2 py-0.5 text-[11px] uppercase tracking-wide text-white/40">
          Coming soon
        </span>
      </div>
      <p className="mt-1.5 text-sm text-white/50">{copy}</p>
    </div>
  );
}

export default function ProfileTab() {
  return (
    <section className="space-y-3">
      <ComingSoon
        title="Profile picture & screen name"
        copy="Upload a picture and choose a screen name other Deadheads will see."
      />
      <ComingSoon
        title="Friends & contacts"
        copy="Add and remove friends to follow what they're listening to."
      />
      <ComingSoon
        title="Listening privacy"
        copy="Control who can see — and hear — what you're playing right now."
      />
    </section>
  );
}
