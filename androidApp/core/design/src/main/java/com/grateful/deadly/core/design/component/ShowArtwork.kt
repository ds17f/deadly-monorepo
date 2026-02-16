package com.grateful.deadly.core.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.grateful.deadly.core.design.R

/**
 * Constructs an Archive.org thumbnail URL for a given recording identifier.
 *
 * Uses the /services/img/ endpoint which returns the item's __ia_thumb.jpg.
 * This is the canonical thumbnail and is consistent across all surfaces.
 */
fun archiveArtworkUrl(recordingId: String): String =
    "https://archive.org/services/img/$recordingId"

/**
 * Archive.org auto-generates waveform spectrograms as thumbnails for audio items
 * that lack real artwork. These are always exactly 180x45 pixels (4:1 aspect ratio).
 * We detect them and show our own placeholder instead.
 */
private fun isWaveformThumbnail(state: AsyncImagePainter.State.Success): Boolean {
    val result = state.result
    val width = result.image.width
    val height = result.image.height
    // Waveforms are 180x45. Also catch "not found" placeholder (160x110).
    // Real artwork is typically 180x140+ and roughly square.
    return height <= 50 || (width > 0 && height > 0 && width.toFloat() / height > 3f)
}

/**
 * Reusable composable that displays artwork for a show/recording.
 *
 * Uses Archive.org's thumbnail API as the primary image source.
 * Detects and rejects auto-generated waveform spectrograms (180x45),
 * showing the placeholder instead.
 * Falls back to [placeholderContent] (or the deadly logo) when
 * the image is loading, fails, is a waveform, or [recordingId] is null.
 *
 */
@Composable
fun ShowArtwork(
    recordingId: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    placeholderContent: @Composable (() -> Unit)? = null
) {
    val fallback: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (placeholderContent != null) {
                placeholderContent()
            } else {
                DefaultArtworkPlaceholder()
            }
        }
    }

    // Try pre-resolved image URL first (ticket/photo cover art)
    if (!imageUrl.isNullOrBlank()) {
        var imageUrlFailed by remember(imageUrl) { mutableStateOf(false) }

        if (!imageUrlFailed) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier,
                loading = { fallback() },
                error = {
                    imageUrlFailed = true
                },
                success = {
                    SubcomposeAsyncImageContent(contentScale = ContentScale.Crop)
                }
            )
            return
        }
    }

    // Fall through to archive.org thumbnail
    if (recordingId.isNullOrBlank()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            fallback()
        }
        return
    }

    val url = archiveArtworkUrl(recordingId)

    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { fallback() },
        error = { fallback() },
        success = { state ->
            if (isWaveformThumbnail(state)) {
                fallback()
            } else {
                SubcomposeAsyncImageContent(contentScale = ContentScale.Crop)
            }
        }
    )
}

@Composable
private fun DefaultArtworkPlaceholder() {
    Image(
        painter = painterResource(R.drawable.deadly_logo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}
