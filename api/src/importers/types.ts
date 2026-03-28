/** Result returned by any artist importer. */
export interface ImportResult {
  showsProcessed: number;
  showsCreated: number;
  recordingsProcessed: number;
  recordingsCreated: number;
  collectionsProcessed: number;
  error?: string;
}

/** Progress callback for live status updates. */
export type ImportProgress = (message: string) => void;

/**
 * Every artist importer implements this interface.
 * The `run` method reads from its configured data source and upserts
 * shows/recordings/collections into catalog.db.
 */
export interface ArtistImporter {
  /** Human-readable name for pipeline_runs.collector_type */
  readonly collectorType: string;

  /** Run the import. Throws on fatal errors. */
  run(artistId: string, onProgress?: ImportProgress): Promise<ImportResult>;
}
