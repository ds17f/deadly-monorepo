/**
 * setlist.fm show source — fetches setlists via the setlist.fm REST API.
 * Requires a free API key (https://api.setlist.fm/).
 */
import type { ImportProgress } from "../types.js";
import type { ShowData, ShowSource } from "./types.js";

const BASE_URL = "https://api.setlist.fm/rest/1.0";
const ITEMS_PER_PAGE = 20;
const REQUEST_DELAY_MS = 500; // 2 req/sec limit
const MAX_RETRIES = 5;
const INITIAL_BACKOFF_MS = 5_000;

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Convert setlist.fm "dd-MM-yyyy" to "YYYY-MM-DD". */
function convertDate(eventDate: string): string {
  const [dd, mm, yyyy] = eventDate.split("-");
  return `${yyyy}-${mm}-${dd}`;
}

// ── setlist.fm API response shapes ──────────────────────────────

interface SfmSong {
  name: string;
  cover?: { name: string };
  tape?: boolean;
  info?: string;
}

interface SfmSet {
  name?: string;    // "Set 1", "Encore", etc.
  encore?: number;
  song: SfmSong[];
}

interface SfmSetlist {
  eventDate: string;
  venue: {
    name: string;
    city: {
      name: string;
      state?: string;
      stateCode?: string;
      country: { code: string; name: string };
    };
  };
  sets: { set: SfmSet[] };
  info?: string;
}

interface SfmResponse {
  setlist: SfmSetlist[];
  total: number;
  page: number;
  itemsPerPage: number;
}

// ── Implementation ──────────────────────────────────────────────

export class SetlistFmSource implements ShowSource {
  readonly name = "setlist.fm";

  constructor(
    private readonly musicbrainzId: string,
    private readonly apiKey: string,
  ) {}

  async fetchShows(onProgress?: ImportProgress): Promise<ShowData[]> {
    const shows: ShowData[] = [];
    let page = 1;
    let totalPages = 1;

    while (page <= totalPages) {
      if (page > 1) await sleep(REQUEST_DELAY_MS);

      const url = `${BASE_URL}/artist/${this.musicbrainzId}/setlists?p=${page}`;
      let data: SfmResponse | null = null;

      for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
        const res = await fetch(url, {
          headers: {
            "x-api-key": this.apiKey,
            "Accept": "application/json",
          },
        });

        if (res.ok) {
          data = (await res.json()) as SfmResponse;
          break;
        }

        if (res.status === 429 && attempt < MAX_RETRIES) {
          const backoff = INITIAL_BACKOFF_MS * Math.pow(2, attempt);
          onProgress?.(`setlist.fm: rate limited on page ${page}, retrying in ${(backoff / 1000).toFixed(0)}s (attempt ${attempt + 1}/${MAX_RETRIES})...`);
          await sleep(backoff);
          continue;
        }

        throw new Error(`setlist.fm API error: HTTP ${res.status} on page ${page}`);
      }

      if (!data) {
        throw new Error(`setlist.fm: exhausted ${MAX_RETRIES} retries on page ${page}`);
      }

      if (page === 1) {
        totalPages = Math.ceil(data.total / ITEMS_PER_PAGE);
        onProgress?.(`setlist.fm: fetching ${data.total} setlists (${totalPages} pages)...`);
      }

      for (const sl of data.setlist) {
        shows.push(mapSetlist(sl));
      }

      onProgress?.(`setlist.fm: page ${page}/${totalPages} (${shows.length} shows)`);
      page++;
    }

    onProgress?.(`setlist.fm: fetched ${shows.length} shows.`);
    return shows;
  }
}

function mapSetlist(sl: SfmSetlist): ShowData {
  const date = convertDate(sl.eventDate);
  const sets = sl.sets?.set ?? [];

  // Build setlist_raw in our schema format
  const setlistRaw = sets.map((s, i) => ({
    set_name: s.name ?? (s.encore ? `Encore${s.encore > 1 ? ` ${s.encore}` : ""}` : `Set ${i + 1}`),
    songs: s.song
      .filter((song) => song.name) // skip empty placeholder songs
      .map((song) => ({
        name: song.name,
        ...(song.cover ? { cover: song.cover.name } : {}),
        ...(song.info ? { info: song.info } : {}),
      })),
  }));

  // Flatten song names for FTS
  const songList = sets
    .flatMap((s) => s.song)
    .filter((song) => song.name)
    .map((song) => song.name)
    .join(" ");

  const hasSongs = songList.length > 0;

  const city = sl.venue.city;

  return {
    date,
    venue: sl.venue.name,
    city: city.name || null,
    state: city.stateCode || city.state || null,
    country: city.country.code,
    setlist_status: hasSongs ? "found" : "missing",
    setlist_raw: setlistRaw,
    song_list: songList,
    primary_source: "setlist.fm",
  };
}
