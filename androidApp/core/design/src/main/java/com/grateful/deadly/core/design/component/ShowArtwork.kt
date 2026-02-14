package com.grateful.deadly.core.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
 */
fun archiveArtworkUrl(recordingId: String): String =
    "https://archive.org/services/img/$recordingId"

/**
 * Constructs a higher-resolution Archive.org image URL for a given recording identifier.
 * Returns ~900px images when available vs. 180px from the thumbnail API.
 */
fun archiveArtworkUrlHighRes(recordingId: String): String =
    "https://archive.org/services/get-item-image.php?identifier=$recordingId"

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
 * Set [highRes] to true for large display surfaces (e.g. playlist header)
 * to load ~900px images instead of 180px thumbnails.
 */
@Composable
fun ShowArtwork(
    recordingId: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    highRes: Boolean = false,
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

    if (recordingId.isNullOrBlank()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            fallback()
        }
        return
    }

    val url = if (highRes) archiveArtworkUrlHighRes(recordingId) else archiveArtworkUrl(recordingId)

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
