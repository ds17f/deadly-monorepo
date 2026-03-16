import Image from "next/image";
import Link from "next/link";
import AppStoreBadge from "@/components/AppStoreBadge";
import { getAllShowIds, getShowById } from "@/lib/shows";

const PLAY_STORE_URL =
  "https://play.google.com/store/apps/details?id=com.grateful.deadly";
const GOOGLE_PLAY_BADGE_URL =
  "https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png";

const FEATURED_SHOW_IDS = [
  "1977-05-08-barton-hall-cornell-university-ithaca-ny-usa",
  "1972-08-27-old-renaissance-faire-grounds-veneta-or-usa",
  "1970-02-13-fillmore-east-new-york-ny-usa",
  "1989-07-07-jfk-stadium-philadelphia-pa-usa",
  "1977-05-09-war-memorial-auditorium-buffalo-ny-usa",
];

function formatDate(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export default function Home() {
  const allIds = getAllShowIds();

  const featured = FEATURED_SHOW_IDS.map((id) => {
    try {
      return getShowById(id);
    } catch {
      return null;
    }
  }).filter((s) => s !== null);

  return (
    <div>
      <div className="mb-12 text-center">
        <Image
          src="/logo.png"
          alt="The Deadly logo"
          width={64}
          height={64}
          className="mx-auto mb-4"
        />
        <h1 className="text-4xl font-bold text-white md:text-5xl">
          The Deadly
        </h1>
        <p className="mt-3 text-lg text-white/60">
          Listen to every Grateful Dead show, free.
        </p>
        <p className="mt-1 text-sm text-white/40">
          {allIds.length.toLocaleString()} shows, 1965&ndash;1995.
        </p>
        <div className="mt-6 flex items-center justify-center gap-3">
          <a
            href={PLAY_STORE_URL}
            target="_blank"
            rel="noopener noreferrer"
          >
            <Image
              src={GOOGLE_PLAY_BADGE_URL}
              alt="Get it on Google Play"
              width={160}
              height={48}
              unoptimized
            />
          </a>
          <AppStoreBadge width={160} height={48} />
        </div>
      </div>

      {featured.length > 0 && (
        <section>
          <h2 className="mb-4 text-lg font-bold text-deadly-title">Featured Shows</h2>
          <div className="space-y-3">
            {featured.map((show) => (
              <Link
                key={show.show_id}
                href={`/shows/${show.show_id}`}
                className="block rounded-lg bg-deadly-surface p-4 transition-colors hover:bg-white/10"
              >
                <div className="font-medium text-white">
                  {formatDate(show.date)}
                </div>
                <div className="text-white">{show.venue}</div>
                <div className="text-sm text-deadly-heading">
                  {show.city}, {show.state}
                </div>
                <div className="mt-1 flex gap-3 text-xs text-white/50">
                  {show.recording_count > 0 && (
                    <span>{show.recording_count} recordings</span>
                  )}
                  {show.avg_rating > 0 && (
                    <span>
                      {"\u2605"} {show.avg_rating.toFixed(1)}
                    </span>
                  )}
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
