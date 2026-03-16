import type { Show } from "@/types/show";

function formatDate(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString("en-US", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

function StarRating({ rating }: { rating: number }) {
  const full = Math.floor(rating);
  const half = rating - full >= 0.25 && rating - full < 0.75;
  const stars: string[] = [];
  for (let i = 0; i < full; i++) stars.push("\u2605");
  if (half) stars.push("\u00BD");
  return (
    <span className="text-deadly-star" title={`${rating.toFixed(1)} stars`}>
      {stars.join("")}{" "}
      <span className="text-sm text-white/60">{rating.toFixed(1)}</span>
    </span>
  );
}

export default function ShowHeader({ show }: { show: Show }) {
  return (
    <header>
      <p className="text-lg font-bold uppercase tracking-wider text-deadly-title">
        Grateful Dead
      </p>
      <h1 className="mt-1 text-2xl font-bold text-white md:text-3xl">
        {formatDate(show.date)}
      </h1>
      <h2 className="mt-1 text-xl font-semibold text-white/80">{show.venue}</h2>
      <p className="text-deadly-heading">
        {show.city}, {show.state}
      </p>
      {show.recording_count > 0 && (
        <div className="mt-3 flex items-center gap-4 text-sm text-white/70">
          <span>
            {show.recording_count} recording
            {show.recording_count !== 1 ? "s" : ""}
          </span>
          {show.avg_rating > 0 && <StarRating rating={show.avg_rating} />}
        </div>
      )}
      {show.ai_show_review?.summary && (
        <p className="mt-3 text-sm italic text-white/50">
          {show.ai_show_review.summary}
        </p>
      )}
    </header>
  );
}
