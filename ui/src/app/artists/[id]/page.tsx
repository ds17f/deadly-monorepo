import ArtistPageClient from "./ArtistPageClient";

export function generateStaticParams() {
  return [{ id: "_" }];
}

export default function ArtistPage() {
  return <ArtistPageClient />;
}
