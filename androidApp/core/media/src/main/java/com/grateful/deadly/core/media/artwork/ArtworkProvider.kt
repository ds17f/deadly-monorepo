package com.grateful.deadly.core.media.artwork

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.grateful.deadly.core.media.R
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * ContentProvider that serves artwork for recordings, filtering out
 * Archive.org waveform spectrograms and returning the deadly logo instead.
 *
 * URI format: content://com.grateful.deadly.artwork/{recordingId}
 *
 * Android Auto, notifications, and other MediaSession consumers use
 * artworkUri from MediaMetadata. By routing through this provider,
 * all consumers get waveform-filtered artwork without needing their
 * own detection logic.
 *
 * Images are cached to disk to avoid redundant downloads.
 */
class ArtworkProvider : ContentProvider() {

    companion object {
        private const val TAG = "ArtworkProvider"
        const val AUTHORITY = "com.grateful.deadly.artwork"

        fun buildUri(recordingId: String): Uri =
            Uri.parse("content://$AUTHORITY/$recordingId")
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val recordingId = uri.lastPathSegment ?: return null
        val ctx = context ?: return null
        val cacheDir = File(ctx.cacheDir, "artwork").also { it.mkdirs() }
        val cachedFile = File(cacheDir, "$recordingId.img")

        // Serve from cache if available
        if (cachedFile.exists()) {
            return ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        return try {
            val archiveUrl = "https://archive.org/services/img/$recordingId"
            val bytes = URL(archiveUrl).readBytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            if (bitmap != null && isWaveform(bitmap)) {
                Log.d(TAG, "Waveform detected for $recordingId (${bitmap.width}x${bitmap.height}), using logo")
                // Write logo to cache
                val logoBitmap = BitmapFactory.decodeResource(ctx.resources, R.drawable.deadly_logo_square)
                FileOutputStream(cachedFile).use { out ->
                    logoBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } else {
                // Write original image to cache
                cachedFile.writeBytes(bytes)
            }

            ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load artwork for $recordingId", e)
            // On error, serve the logo
            try {
                val logoBitmap = BitmapFactory.decodeResource(ctx.resources, R.drawable.deadly_logo_square)
                FileOutputStream(cachedFile).use { out ->
                    logoBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to write fallback logo", e2)
                null
            }
        }
    }

    private fun isWaveform(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        return h <= 50 || (w > 0 && h > 0 && w.toFloat() / h > 3f)
    }

    // Required ContentProvider overrides (unused)
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String = "image/*"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
