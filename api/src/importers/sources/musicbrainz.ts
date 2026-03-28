/**
 * MusicBrainz artist lookup — resolves an artist name to a MusicBrainz ID.
 * Free API, no auth required. Rate limit: 1 req/sec.
 */

interface MBSearchResult {
  artists: {
    id: string;
    name: string;
    type?: string;
    score: number;
  }[];
}

/**
 * Look up a MusicBrainz ID by artist name. Returns the MBID of the
 * highest-scoring result, or null if no confident match is found.
 *
 * Only returns a result with score >= 90 to avoid false matches.
 */
export async function lookupMusicBrainzId(artistName: string): Promise<string | null> {
  const url = `https://musicbrainz.org/ws/2/artist/?query=artist:${encodeURIComponent(artistName)}&fmt=json&limit=3`;

  const res = await fetch(url, {
    headers: { "User-Agent": "TheDeadlyApp/1.0 (https://thedeadly.app)" },
  });

  if (!res.ok) return null;

  const data = (await res.json()) as MBSearchResult;

  // Only use high-confidence matches
  const best = data.artists?.[0];
  if (!best || best.score < 90) return null;

  return best.id;
}
