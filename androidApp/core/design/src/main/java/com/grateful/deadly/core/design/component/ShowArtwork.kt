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
import com.grateful.deadly.core.model.RecordingSourceType
import com.grateful.deadly.core.model.ShowArtworkService
import com.grateful.deadly.core.model.SourceBadgeStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

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
    val resolvedSourceType = recordingId?.let { ShowArtworkService.sourceType(it) }

    Box(modifier = modifier) {
        ShowArtworkContent(
            recordingId = recordingId,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            imageUrl = imageUrl,
            placeholderContent = placeholderContent
        )

        if (resolvedSourceType != null && resolvedSourceType != RecordingSourceType.UNKNOWN) {
            val style = ShowArtworkService.badgeStyle
            when (style) {
                SourceBadgeStyle.NONE -> { /* badge disabled */ }
                SourceBadgeStyle.SHORT, SourceBadgeStyle.LONG -> {
                    val label = if (style == SourceBadgeStyle.SHORT) resolvedSourceType.badgeLabel else resolvedSourceType.displayName
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                SourceBadgeStyle.ICON -> {
                    val iconRes = when (resolvedSourceType) {
                        RecordingSourceType.SOUNDBOARD -> R.drawable.ic_equalizer
                        RecordingSourceType.AUDIENCE -> R.drawable.ic_mic
                        RecordingSourceType.FM -> R.drawable.ic_radio
                        RecordingSourceType.MATRIX -> R.drawable.ic_merge
                        RecordingSourceType.REMASTER -> R.drawable.ic_auto_fix
                        else -> null
                    }
                    if (iconRes != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .padding(3.dp)
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = resolvedSourceType.displayName,
                                modifier = Modifier.size(10.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowArtworkContent(
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
        painter = painterResource(R.drawable.deadly_logo_square),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}
