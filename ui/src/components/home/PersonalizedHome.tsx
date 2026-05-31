"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/contexts/AuthContext";
import { fetchRecentShows, fetchFavoriteShows } from "@/lib/userDataApi";
import type { RecentShow, FavoriteShow } from "@/types/userdata";
import ShowRow from "@/components/show/ShowRow";
import { formatShowDate, formatLocation } from "@/components/show/showFormat";

const MAX = 6;

function Section({
  title,
  seeAllHref,
  children,
}: {
  title: string;
  seeAllHref: string;
  children: React.ReactNode;
}) {
  return (
    <div className="mb-8">
      <div className="mb-3 flex items-baseline justify-between">
        <h3 className="text-lg font-semibold text-white">{title}</h3>
        <Link
          href={seeAllHref}
          className="text-sm text-white/50 transition hover:text-white"
        >
          See all →
        </Link>
      </div>
      <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {children}
      </ul>
    </div>
  );
}

// Personalized strip shown at the top of the home page for signed-in users.
// Returns null when signed out / still resolving / no data, so the normal
// (static, SEO-friendly) landing home is unaffected.
export default function PersonalizedHome() {
  const { user, isLoading } = useAuth();
  const [recent, setRecent] = useState<RecentShow[] | null>(null);
  const [favorites, setFavorites] = useState<FavoriteShow[] | null>(null);

  useEffect(() => {
    if (!user?.id) return;
    let cancelled = false;
    fetchRecentShows()
      .then((s) => !cancelled && setRecent(s))
      .catch(() => !cancelled && setRecent([]));
    fetchFavoriteShows()
      .then((s) => !cancelled && setFavorites(s))
      .catch(() => !cancelled && setFavorites([]));
    return () => {
      cancelled = true;
    };
  }, [user?.id]);

  if (isLoading || !user) return null;

  const recentTop = (recent ?? []).slice(0, MAX);
  const favTop = [...(favorites ?? [])]
    .sort((a, b) =>
      a.isPinned !== b.isPinned ? (a.isPinned ? -1 : 1) : b.addedAt - a.addedAt
    )
    .slice(0, MAX);

  // Signed in but nothing to show yet — defer to the normal home.
  if (recentTop.length === 0 && favTop.length === 0) return null;

  const firstName = user.name?.split(" ")[0];

  return (
    <section className="mb-10">
      <h2 className="mb-5 text-2xl font-bold text-white">
        {firstName ? `Welcome back, ${firstName}` : "Welcome back"}
      </h2>

      {recentTop.length > 0 && (
        <Section title="Pick up where you left off" seeAllHref="/me/recent">
          {recentTop.map((show) => (
            <li key={show.showId}>
              <ShowRow
                showId={show.showId}
                image={show.image}
                bestRecordingId={show.bestRecordingId}
                date={formatShowDate(show)}
                location={formatLocation(show)}
                venue={show.venue}
              />
            </li>
          ))}
        </Section>
      )}

      {favTop.length > 0 && (
        <Section title="Your favorites" seeAllHref="/me/favorites">
          {favTop.map((show) => (
            <li key={show.showId}>
              <ShowRow
                showId={show.showId}
                image={show.image}
                bestRecordingId={show.bestRecordingId}
                date={formatShowDate(show)}
                location={formatLocation(show)}
                venue={show.venue}
                pinned={show.isPinned}
              />
            </li>
          ))}
        </Section>
      )}
    </section>
  );
}
