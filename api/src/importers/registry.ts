/**
 * Importer registry — maps collector_type strings to ArtistImporter
 * implementations. The admin UI reads availableCollectors to populate
 * the collector type dropdown.
 */
import type { ArtistImporter } from "./types.js";
import { GratefulDeadImporter } from "./grateful-dead.js";

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
  // To add a new collector:
  // 1. Create a class implementing ArtistImporter in api/src/importers/
  // 2. Add a case to getImporter() below
  // 3. Add a CollectorInfo entry to this array
];

/**
 * Get the importer for an artist based on its collector_type.
 * Returns null if no importer matches.
 */
export function getImporter(artistId: string, dataSources: Record<string, string>): ArtistImporter | null {
  const collectorType = dataSources.collector_type;

  switch (collectorType) {
    case "stage02-json":
      return new GratefulDeadImporter();
    default:
      return null;
  }
}
