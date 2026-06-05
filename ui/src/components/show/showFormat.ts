// Shared display helpers for /me library rows (Recent, Favorites). The API
// enriches rows with `date`/`city`/`state`; when `date` is missing we fall
// back to the leading YYYY-MM-DD of the date-prefixed showId slug.

export function formatShowDate(show: {
  showId: string;
  date?: string | null;
}): string {
  const iso = (show.date ?? show.showId).slice(0, 10);
  const d = new Date(`${iso}T00:00:00`);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

// City + state when known, e.g. "Ithaca, NY".
export function formatLocation(show: {
  city?: string | null;
  state?: string | null;
}): string | null {
  const parts = [show.city, show.state].filter(Boolean);
  return parts.length ? parts.join(", ") : null;
}
