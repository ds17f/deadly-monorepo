import CatchAllClient from "./CatchAllClient";

export function generateStaticParams() {
  return [{ slug: ["_"] }];
}

export default function CatchAllPage() {
  return <CatchAllClient />;
}
