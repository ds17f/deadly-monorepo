package com.grateful.deadly.core.media.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.grateful.deadly.core.media.R

/**
 * BitmapLoader that detects Archive.org waveform spectrograms and replaces
 * them with the deadly logo. Waveforms are auto-generated 180x45 grayscale
 * images for audio items that lack uploaded artwork.
 *
 * Wraps [DataSourceBitmapLoader] for the actual download, then checks the
 * decoded bitmap dimensions before returning it.
 */
@UnstableApi
class WaveformFilteringBitmapLoader(context: Context) : BitmapLoader {

    private val delegate = DataSourceBitmapLoader(context)
    private val logoBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.deadly_logo)
    }

    override fun supportsMimeType(mimeType: String): Boolean =
        delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        delegate.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val isArchiveUrl = uri.host?.contains("archive.org") == true
        if (!isArchiveUrl) return delegate.loadBitmap(uri)

        return Futures.transform(
            delegate.loadBitmap(uri),
            { bitmap ->
                if (bitmap != null && isWaveform(bitmap)) logoBitmap else bitmap
            },
            Runnable::run
        )
    }

    private fun isWaveform(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        return h <= 50 || (w > 0 && h > 0 && w.toFloat() / h > 3f)
    }
}
