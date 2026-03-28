/**
 * Importer registry — maps collector_type strings to ArtistImporter
 * implementations. The admin UI reads availableCollectors to populate
 * the collector type dropdown.
 */
import type { ArtistImporter } from "./types.js";
import { GratefulDeadImporter } from "./grateful-dead.js";
import { GenericImporter } from "./generic-importer.js";
import { SetlistFmSource } from "./sources/setlistfm.js";
import { IARecordingSource } from "./sources/ia-recordings.js";
import { getCatalogDb, updateArtist } from "../db/catalog.js";
import { lookupMusicBrainzId } from "./sources/musicbrainz.js";

export interface CollectorInfo {
  type: string;
  name: string;
  description: string;
  help: string;
}

/**
 * All registered collector types. The admin UI reads this to populate
 * the dropdown and show help text. Only add a collector here once it's
 * actually implemented and ready to use.
 */
export const availableCollectors: CollectorInfo[] = [
  {
    type: "stage02-json",
    name: "Stage 02 JSON (data.zip)",
    description: "Downloads data.zip from GitHub Releases and imports pre-processed show, recording, and collection data.",
    help: "Automatically downloads the latest data.zip from the ds17f/deadly-monorepo GitHub release, "
      + "extracts it to a temp directory, imports, and cleans up. The version is read from data/version "
      + "(currently 2.3.0). Override with GD_DATA_VERSION env var. If GD_DATA_DIR is set and the directory "
      + "exists, it uses that instead of downloading. This is the importer for the Grateful Dead's curated dataset.",
  },
  {
    type: "ia-setlistfm",
    name: "Internet Archive + setlist.fm",
    description: "Fetches recordings from Internet Archive and setlists from setlist.fm, merged by date.",
    help: "Requires the artist to have ia_collection and musicbrainz_id set. "
      + "Uses SETLISTFM_API_KEY env var for setlist.fm API access. "
      + "Rate-limited to 2 req/sec for setlist.fm. "
      + "Creates shows from setlists, links IA recordings by date, computes best recordings.",
  },
];

/**
 * Get the importer for an artist based on its collector_type.
 * Returns null if no importer matches.
 *
 * Async because it may auto-resolve the MusicBrainz ID from the
 * artist name if not already set.
 */
export async function getImporter(artistId: string, dataSources: Record<string, string>): Promise<ArtistImporter | null> {
  const collectorType = dataSources.collector_type;

  switch (collectorType) {
    case "stage02-json": {
      // Auto-set image_url from IA collection thumbnail if not already set
      const db2 = getCatalogDb();
      const gdArtist = db2.prepare(
        "SELECT ia_collection, image_url FROM artists WHERE id = ?",
      ).get(artistId) as { ia_collection: string | null; image_url: string | null } | undefined;
      if (gdArtist && !gdArtist.image_url && gdArtist.ia_collection) {
        updateArtist(artistId, {
          image_url: `https://archive.org/services/img/${encodeURIComponent(gdArtist.ia_collection)}`,
        });
      }
      return new GratefulDeadImporter();
    }

    case "ia-setlistfm": {
      const db = getCatalogDb();
      const artist = db.prepare(
        "SELECT name, ia_collection, musicbrainz_id, image_url FROM artists WHERE id = ?",
      ).get(artistId) as { name: string; ia_collection: string | null; musicbrainz_id: string | null; image_url: string | null } | undefined;

      if (!artist?.ia_collection) {
        throw new Error(
          `Artist "${artistId}" requires ia_collection for ia-setlistfm importer`,
        );
      }

      // Auto-resolve MusicBrainz ID if not set
      let mbid = artist.musicbrainz_id;
      if (!mbid) {
        mbid = await lookupMusicBrainzId(artist.name);
        if (!mbid) {
          throw new Error(
            `Could not auto-resolve MusicBrainz ID for "${artist.name}". `
            + `Set musicbrainz_id manually on the artist record.`,
          );
        }
        // Save it so we don't look it up again
        updateArtist(artistId, { musicbrainz_id: mbid });
      }

      const apiKey = process.env.SETLISTFM_API_KEY;
      if (!apiKey) {
        throw new Error("SETLISTFM_API_KEY env var is required for ia-setlistfm importer");
      }

      // Auto-set image_url from IA collection thumbnail if not already set
      if (!artist.image_url && artist.ia_collection) {
        const imageUrl = `https://archive.org/services/img/${encodeURIComponent(artist.ia_collection)}`;
        updateArtist(artistId, { image_url: imageUrl });
      }

      return new GenericImporter(
        new SetlistFmSource(mbid, apiKey),
        new IARecordingSource(artist.ia_collection),
        "ia-setlistfm",
      );
    }

    default:
      return null;
  }
}
