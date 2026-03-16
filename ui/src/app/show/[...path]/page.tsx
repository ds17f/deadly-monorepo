import { redirect } from "next/navigation";
import { getBuildShowIds } from "@/lib/shows";

export async function generateStaticParams() {
  const showIds = getBuildShowIds();
  const params: { path: string[] }[] = [];
  for (const id of showIds) {
    // /show/{id} — bare show link
    params.push({ path: [id] });
  }
  return params;
}

export default async function ShowRedirect({
  params,
}: {
  params: Promise<{ path: string[] }>;
}) {
  const { path } = await params;
  // path[0] is the show ID, ignore /recording/{id}/track/{n} segments
  const showId = path[0];
  redirect(`/shows/${showId}`);
}
