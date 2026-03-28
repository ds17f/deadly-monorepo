import ShowPageClient from "./ShowPageClient";

// Generate a single placeholder so Next.js static export creates a shell HTML.
// Caddy falls back to this for any /shows/{id} not pre-rendered.
export function generateStaticParams() {
  return [{ id: "_" }];
}

export default function ShowPage() {
  return <ShowPageClient />;
}
