package com.grateful.deadly.core.database.service

import android.util.Log
import com.grateful.deadly.core.model.AppDatabase
import com.grateful.deadly.core.database.DeadlyDatabase
import com.grateful.deadly.core.database.dao.RecordingDao
import com.grateful.deadly.core.database.dao.ShowDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports the prebuilt catalog seed (`catalog.db`) into the live, already-migrated
 * Room database using ATTACH + bulk `INSERT…SELECT`.
 *
 * Why not just copy the seed file over the Room DB? The seed is a **neutral
 * catalog-only** database — it has no Room
 * identity hash (`room_master_table`) and none of the device-local tables
 * (favorites, library, recents, reviews, sync outbox). Opening it as the live ORM
 * DB would crash or lose user data. Instead we keep Room's own migrated DB and copy
 * the catalog rows into it. See `docs/adr/0007-prebuilt-catalog-db.md`.
 *
 * Flow (catalog writes in one transaction so the health gate — a committed
 * `data_version` row — flips only on success):
 *   1. ATTACH the seed read-only as `seed`.
 *   2. In a txn: clear the childless catalog tables → **upsert** `shows` (never
 *      delete it, so CASCADE children holding user data survive) → copy
 *      `recordings`/`dead_collections` → rebuild the `show_search` FTS on-device
 *      → copy `data_version` LAST.
 *   3. DETACH, reconcile denormalized favorite flags, report counts.
 *
 * `shows` is upserted rather than wiped because it is the parent of CASCADE
 * children that hold user data (favorites, reviews, recording prefs, player
 * tags). Deleting it would silently take them with it. See
 * `docs/adr/0009-non-destructive-catalog-refresh.md`.
 *
 * FTS is rebuilt rather than shipped: `show_search.searchText` is a computed blob
 * (multi-format dates, member/song lists, source tags) — see [ShowSearchText],
 * shared with the JSON import path so search is identical regardless of source.
 */
@Singleton
class SeedDatabaseImportService @Inject constructor(
    private val database: DeadlyDatabase,
    @AppDatabase private val showDao: ShowDao,
    @AppDatabase private val recordingDao: RecordingDao
) {
    companion object {
        private const val TAG = "SeedDatabaseImportService"
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray()

        // Catalog column lists. Seed tables hold exactly these (see
        // data/catalog_schema.json); `shows` in Room additionally has the
        // device-local isFavorite/favoritedAt, which we default on copy.
        private const val SHOW_CATALOG_COLS =
            "showId,date,year,month,yearMonth,band,url,venueName,city,state,country," +
            "locationRaw,setlistStatus,setlistRaw,songList,lineupStatus,lineupRaw,memberList," +
            "showSequence,recordingsRaw,recordingCount,bestRecordingId,bestSourceType," +
            "averageRating,totalReviews,coverImageUrl,createdAt,updatedAt"

        private const val RECORDING_COLS =
            "identifier,show_id,source_type,rating,raw_rating,review_count,confidence," +
            "high_ratings,low_ratings,taper,source,lineage,source_type_string,collection_timestamp"

        private const val COLLECTION_COLS =
            "id,name,description,tagsJson,showIdsJson,totalShows,primaryTag,createdAt,updatedAt"

        private const val DATA_VERSION_COLS =
            "id,dataVersion,packageName,versionType,description,importedAt,gitCommit,gitTag," +
            "buildTimestamp,totalShows,totalVenues,totalFiles,totalSizeBytes"

        // `col=excluded.col` for every catalog column except the conflict key
        // (showId) and the device-local favorite columns, which the upsert must
        // not touch. Derived from SHOW_CATALOG_COLS so it can't drift.
        private val SHOW_UPSERT_ASSIGNMENTS =
            SHOW_CATALOG_COLS.split(",")
                .map { it.trim() }
                .filter { it != "showId" }
                .joinToString(", ") { col -> "$col=excluded.$col" }
    }

    suspend fun importFromSeed(
        seedFile: File,
        progressCallback: ((SeedImportProgress) -> Unit)? = null
    ): SeedImportResult = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting seed import from ${seedFile.name} (${seedFile.length()} bytes)")
            progressCallback?.invoke(SeedImportProgress("VALIDATING", "Validating catalog seed..."))

            validateSeedFile(seedFile)?.let { return@withContext it }

            val db = database.openHelper.writableDatabase
            val seedPathEscaped = seedFile.absolutePath.replace("'", "''")

            // ATTACH must happen outside a transaction.
            db.execSQL("ATTACH DATABASE '$seedPathEscaped' AS seed")
            try {
                val seedShowCount = queryLong(db, "SELECT COUNT(*) FROM seed.shows")
                if (seedShowCount <= 0L) {
                    return@withContext SeedImportResult.Error("Seed contains no shows — refusing to import")
                }
                Log.i(TAG, "Seed validated: $seedShowCount shows")

                progressCallback?.invoke(SeedImportProgress("COPYING", "Loading catalog..."))
                db.beginTransaction()
                try {
                    // Clear the childless catalog/derived tables (no user data hangs off
                    // these). `shows` is NOT deleted — it has CASCADE children holding
                    // user data (favorites, reviews, prefs, …); we upsert it instead.
                    // See docs/adr/0009-non-destructive-catalog-refresh.md.
                    db.execSQL("DELETE FROM show_search")
                    db.execSQL("DELETE FROM recordings")
                    db.execSQL("DELETE FROM dead_collections")
                    db.execSQL("DELETE FROM data_version")

                    // Shows: upsert catalog columns by showId. On insert, default the
                    // device-local favorite state; on conflict, update catalog columns
                    // only and leave isFavorite/favoritedAt alone (reconciled below).
                    // `WHERE true` disambiguates the INSERT…SELECT…ON CONFLICT parse.
                    db.execSQL(
                        "INSERT INTO shows ($SHOW_CATALOG_COLS,isFavorite,favoritedAt) " +
                        "SELECT $SHOW_CATALOG_COLS,0,NULL FROM seed.shows WHERE true " +
                        "ON CONFLICT(showId) DO UPDATE SET $SHOW_UPSERT_ASSIGNMENTS"
                    )
                    db.execSQL("INSERT INTO recordings ($RECORDING_COLS) SELECT $RECORDING_COLS FROM seed.recordings")
                    db.execSQL("INSERT INTO dead_collections ($COLLECTION_COLS) SELECT $COLLECTION_COLS FROM seed.dead_collections")

                    progressCallback?.invoke(SeedImportProgress("INDEXING", "Building search index..."))
                    rebuildSearchIndex(db)

                    // data_version LAST: the health gate keys on this committed row, so it must
                    // only appear once everything else is in place.
                    db.execSQL("INSERT INTO data_version ($DATA_VERSION_COLS) SELECT $DATA_VERSION_COLS FROM seed.data_version")

                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            } finally {
                runCatching { db.execSQL("DETACH DATABASE seed") }
                    .onFailure { Log.w(TAG, "Failed to DETACH seed (non-fatal)", it) }
            }

            // Favorites (and other CASCADE children) were never deleted; re-derive the
            // denormalized favorite flags on the freshly-upserted shows.
            showDao.reconcileFavoriteFlags()

            val showCount = showDao.getShowCount()
            val recordingCount = recordingDao.getRecordingCount()
            progressCallback?.invoke(SeedImportProgress("COMPLETED", "Catalog ready"))
            Log.i(TAG, "✅ Seed import complete: $showCount shows, $recordingCount recordings")
            SeedImportResult.Success(showCount, recordingCount)
        } catch (e: Exception) {
            Log.e(TAG, "Seed import failed", e)
            SeedImportResult.Error(e.message ?: "Seed import failed")
        }
    }

    /**
     * Rebuild the `show_search` FTS table from the freshly-copied catalog, reproducing
     * the exact text the JSON importer would have indexed (via [ShowSearchText]).
     * Source-type tags are derived from each show's recordings (the catalog has no
     * per-show source-type map column).
     */
    private fun rebuildSearchIndex(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // show_id -> set of UPPER(source_type)
        val sourceTypesByShow = HashMap<String, MutableSet<String>>()
        db.query("SELECT show_id, source_type FROM recordings WHERE source_type IS NOT NULL").use { c ->
            val showIdIdx = c.getColumnIndexOrThrow("show_id")
            val srcIdx = c.getColumnIndexOrThrow("source_type")
            while (c.moveToNext()) {
                val showId = c.getString(showIdIdx) ?: continue
                val src = c.getString(srcIdx)?.uppercase() ?: continue
                sourceTypesByShow.getOrPut(showId) { HashSet() }.add(src)
            }
        }

        // Materialize (showId, searchText) first so we are not writing to show_search
        // while a cursor over `shows` is still open on the same connection.
        val entries = ArrayList<Pair<String, String>>()
        db.query(
            "SELECT showId, date, venueName, locationRaw, memberList, songList, averageRating, totalReviews FROM shows"
        ).use { c ->
            val idIdx = c.getColumnIndexOrThrow("showId")
            val dateIdx = c.getColumnIndexOrThrow("date")
            val venueIdx = c.getColumnIndexOrThrow("venueName")
            val locIdx = c.getColumnIndexOrThrow("locationRaw")
            val memberIdx = c.getColumnIndexOrThrow("memberList")
            val songIdx = c.getColumnIndexOrThrow("songList")
            val avgIdx = c.getColumnIndexOrThrow("averageRating")
            val reviewsIdx = c.getColumnIndexOrThrow("totalReviews")
            while (c.moveToNext()) {
                val showId = c.getString(idIdx)
                val searchText = ShowSearchText.build(
                    date = c.getString(dateIdx),
                    venue = c.getString(venueIdx),
                    locationRaw = if (c.isNull(locIdx)) null else c.getString(locIdx),
                    memberListCsv = if (c.isNull(memberIdx)) null else c.getString(memberIdx),
                    songListCsv = if (c.isNull(songIdx)) null else c.getString(songIdx),
                    sourceTypeKeys = sourceTypesByShow[showId] ?: emptySet(),
                    avgRating = if (c.isNull(avgIdx)) 0.0 else c.getDouble(avgIdx),
                    totalReviews = c.getInt(reviewsIdx)
                )
                entries.add(showId to searchText)
            }
        }

        val insert = db.compileStatement("INSERT INTO show_search (showId, searchText) VALUES (?, ?)")
        for ((showId, searchText) in entries) {
            insert.clearBindings()
            insert.bindString(1, showId)
            insert.bindString(2, searchText)
            insert.executeInsert()
        }
    }

    private fun validateSeedFile(seedFile: File): SeedImportResult.Error? {
        if (!seedFile.exists() || !seedFile.canRead()) {
            return SeedImportResult.Error("Seed file missing or unreadable: ${seedFile.absolutePath}")
        }
        if (seedFile.length() == 0L) {
            return SeedImportResult.Error("Seed file is empty")
        }
        val header = ByteArray(16)
        FileInputStream(seedFile).use { input ->
            if (input.read(header) < 16 || !header.contentEquals(SQLITE_HEADER)) {
                return SeedImportResult.Error("Seed file is not a valid SQLite database")
            }
        }
        return null
    }

    private fun queryLong(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Long {
        db.query(sql).use { c -> return if (c.moveToFirst()) c.getLong(0) else 0L }
    }
}

data class SeedImportProgress(val phase: String, val currentItem: String)

sealed class SeedImportResult {
    data class Success(val showCount: Int, val recordingCount: Int) : SeedImportResult()
    data class Error(val error: String) : SeedImportResult()
}
