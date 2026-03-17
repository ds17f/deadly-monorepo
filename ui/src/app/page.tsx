import { buildShowIndex, getTopRatedShows, buildYearHistogram } from "@/lib/shows";
import { getAllCollections } from "@/lib/collections";
import HomeContent from "@/components/home/HomeContent";

/** Venue substrings to hide from the homepage browse list. */
const HIDDEN_VENUES = ["big nig"];

function isVisibleShow(venue: string): boolean {
  const lower = venue.toLowerCase();
  return !HIDDEN_VENUES.some((h) => lower.includes(h));
}

export default function Home() {
  const fullIndex = buildShowIndex();
  const showIndex = fullIndex.filter((s) => isVisibleShow(s.v));
  const topRated = getTopRatedShows(showIndex, 20);
  const collections = getAllCollections();
  const yearData = buildYearHistogram(showIndex);

  return (
    <HomeContent
      showIndex={showIndex}
      topRatedAll={topRated}
      collections={collections}
      yearData={yearData}
      totalShows={fullIndex.length}
    />
  );
}
