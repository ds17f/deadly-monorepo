package com.grateful.deadly.core.design.component

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.grateful.deadly.core.design.R
import com.grateful.deadly.core.design.resources.IconResources
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun QrCodeDisplay(
    url: String,
    showDate: String,
    venue: String,
    location: String,
    recordingId: String?,
    coverImageUrl: String?,
    songTitle: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var shareBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(url) {
        // QR shown immediately
        val qr = withContext(Dispatchers.Default) {
            generateQrBitmapWithLogo(context, url, 600)
        }
        qrBitmap = qr

        // Share card built in background (loads cover image then composes poster)
        val cover = try {
            loadCoverBitmapForShare(context, coverImageUrl, recordingId)
        } catch (e: Exception) {
            Log.e("QrCodeDisplay", "Failed to load cover for share card", e)
            null
        }
        shareBitmap = withContext(Dispatchers.Default) {
            buildShareCard(context, qr, cover, showDate, venue, location, songTitle)
        }
        cover?.recycle()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top zone (~45%): artwork with gradient scrim and show info overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f)
                ) {
                    ShowArtwork(
                        recordingId = recordingId,
                        contentDescription = "$showDate at $venue",
                        modifier = Modifier.fillMaxSize(),
                        imageUrl = coverImageUrl
                    )

                    // Bottom-edge gradient scrim for text legibility
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.55f)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.82f)
                                    )
                                )
                            )
                    )

                    // Date + venue text overlaid at bottom of artwork zone
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = showDate,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (venue.isNotBlank()) {
                            Text(
                                text = venue,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Bottom zone (~55%): dark surface with QR code, location, share button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.55f)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 24.dp)
                        .padding(top = 28.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // QR code centered at 300dp
                    qrBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(300.dp)
                        )
                    } ?: Box(
                        modifier = Modifier.size(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }

                    // Song title (shown when playing a specific track)
                    if (!songTitle.isNullOrBlank()) {
                        Text(
                            text = "↳ $songTitle",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Location text (skipped when blank)
                    if (location.isNotBlank()) {
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Share the full poster card once built, else fall back to the QR bitmap
                    FilledTonalButton(
                        onClick = { (shareBitmap ?: qrBitmap)?.let { shareQrBitmap(context, it) } },
                        enabled = qrBitmap != null
                    ) {
                        Icon(
                            painter = IconResources.Content.Share(),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share QR Code")
                    }
                }
            }

            // Close button — padded for status bar insets
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(8.dp)
            ) {
                Icon(
                    painter = IconResources.Navigation.Close(),
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}

private fun generateQrBitmapWithLogo(context: Context, content: String, size: Int): Bitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H)
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(
                x, y,
                if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            )
        }
    }

    // Overlay the app logo in the center with a white circle background
    val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.deadly_logo)
    if (logoBitmap != null) {
        val canvas = Canvas(bitmap)
        val centerX = size / 2f
        val centerY = size / 2f
        val circleRadius = size * 0.22f / 2f
        val logoSize = (size * 0.18f).toInt()

        // White circle background
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, circleRadius, paint)

        // Logo centered on top of the white circle
        val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, logoSize, logoSize, true)
        canvas.drawBitmap(
            scaledLogo,
            centerX - logoSize / 2f,
            centerY - logoSize / 2f,
            null
        )
        scaledLogo.recycle()
        logoBitmap.recycle()
    }

    return bitmap
}

/**
 * Load cover image for the share card.
 * Tries Coil cache first (fast if ShowArtwork already loaded it),
 * then falls back to direct download via URLConnection.
 * Uses archive.org thumbnail as fallback when no explicit URL is available.
 * Returns null on any failure.
 */
private suspend fun loadCoverBitmapForShare(
    context: Context,
    imageUrl: String?,
    recordingId: String?
): Bitmap? {
    val url = when {
        !imageUrl.isNullOrBlank() -> imageUrl
        !recordingId.isNullOrBlank() -> "https://archive.org/services/img/$recordingId"
        else -> return null
    }
    Log.d("QrCodeDisplay", "Loading cover from: $url")

    return withContext(Dispatchers.IO) {
        // Try to load via HTTPURLConnection with proper error handling
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 18_000
            connection.instanceFollowRedirects = true
            connection.addRequestProperty("User-Agent", "thedeadly-app/1.0")
            connection.addRequestProperty("Accept", "image/*")

            connection.connect()
            val code = connection.responseCode
            Log.d("QrCodeDisplay", "Cover response: HTTP $code from $url")

            if (code in 200..299) {
                val bitmap = connection.inputStream.buffered().use {
                    BitmapFactory.decodeStream(it)
                }
                Log.d("QrCodeDisplay", "Cover decoded: ${bitmap?.width}x${bitmap?.height}")
                bitmap
            } else {
                Log.w("QrCodeDisplay", "Cover fetch failed: HTTP $code")
                null
            }
        } catch (e: Exception) {
            Log.w("QrCodeDisplay", "Cover load error: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}

/**
 * Compose a 1080×1920 "concert poster" bitmap:
 *   • top half  — cover artwork (center-cropped) or app logo + gradient fade to dark
 *   • bottom half — dark background, QR code with white quiet-zone padding,
 *                   optional location text, app branding
 *
 * Waveform spectrograms from archive.org (height ≤50 or aspect > 3:1) are
 * treated as "no artwork" and fall back to the app logo (same as ShowArtwork).
 */
private fun buildShareCard(
    context: Context,
    qrBitmap: Bitmap,
    coverBitmap: Bitmap?,
    showDate: String,
    venue: String,
    location: String,
    songTitle: String? = null
): Bitmap {
    val W = 1080
    val H = 1920
    val bg = 0xFF0D0D0D.toInt()
    val result = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)

    canvas.drawColor(bg)

    val topH = (H * 0.48f).toInt()
    val validCover = coverBitmap?.takeIf { it.height > 50 && it.width.toFloat() / it.height <= 3f }
    Log.d("QrCodeDisplay", "Share card: cover=${coverBitmap != null}, valid=${validCover != null}, dims=${coverBitmap?.width}x${coverBitmap?.height}")

    // — Cover artwork or app logo (center-crop to fill top zone) —
    val artworkBitmap = if (validCover != null) validCover else null
    val displayBitmap = artworkBitmap ?: run {
        // Fall back to app logo (same as ShowArtwork display)
        try {
            BitmapFactory.decodeResource(context.resources, R.drawable.deadly_logo)
        } catch (e: Exception) {
            Log.w("QrCodeDisplay", "Failed to load logo fallback: ${e.message}")
            null
        }
    }

    if (displayBitmap != null) {
        val aspect = displayBitmap.width.toFloat() / displayBitmap.height
        val targetAspect = W.toFloat() / topH
        val (sw, sh) = if (aspect > targetAspect) {
            ((topH * aspect).toInt()) to topH
        } else {
            W to (W / aspect).toInt()
        }
        val scaled = Bitmap.createScaledBitmap(displayBitmap, sw, sh, true)
        canvas.drawBitmap(scaled, -((sw - W) / 2f), -((sh - topH) / 2f), null)
        scaled.recycle()
        if (artworkBitmap == null) displayBitmap.recycle()  // Only recycle if we created it
    }

    // — Gradient scrim over artwork bottom —
    val gradPaint = Paint()
    gradPaint.shader = android.graphics.LinearGradient(
        0f, topH * 0.35f, 0f, topH.toFloat(),
        intArrayOf(android.graphics.Color.TRANSPARENT, bg),
        null, android.graphics.Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, W.toFloat(), topH.toFloat(), gradPaint)

    // — Show date —
    val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 76f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText(truncateText(showDate, datePaint, W - 120f), 60f, topH - 96f, datePaint)

    // — Venue —
    if (venue.isNotBlank()) {
        val venuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xDDFFFFFF.toInt()
            textSize = 50f
        }
        canvas.drawText(truncateText(venue, venuePaint, W - 120f), 60f, topH - 32f, venuePaint)
    }

    // — Song title (below venue, in the dark bottom zone) —
    if (!songTitle.isNullOrBlank()) {
        val songPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 44f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
        }
        canvas.drawText(truncateText("↳ $songTitle", songPaint, W - 120f), 60f, topH + 60f, songPaint)
    }

    // — QR code (white quiet-zone padding + centered in bottom zone) —
    val qrSize = (W * 0.62f).toInt()
    val qrPad = 28
    val qrLeft = (W - qrSize) / 2
    val qrTop = topH + ((H - topH - qrSize) * 0.40f).toInt()

    val qrBgPaint = Paint().apply { color = android.graphics.Color.WHITE }
    canvas.drawRect(
        (qrLeft - qrPad).toFloat(), (qrTop - qrPad).toFloat(),
        (qrLeft + qrSize + qrPad).toFloat(), (qrTop + qrSize + qrPad).toFloat(),
        qrBgPaint
    )
    val scaledQr = Bitmap.createScaledBitmap(qrBitmap, qrSize, qrSize, true)
    canvas.drawBitmap(scaledQr, qrLeft.toFloat(), qrTop.toFloat(), null)
    scaledQr.recycle()

    // — Location —
    if (location.isNotBlank()) {
        val locPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt()
            textSize = 42f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            truncateText(location, locPaint, (W - 80f)),
            W / 2f, (qrTop + qrSize + qrPad + 64).toFloat(), locPaint
        )
    }

    // — App branding —
    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55FFFFFF.toInt()
        textSize = 34f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("thedeadly.app", W / 2f, H - 56f, brandPaint)

    return result
}

private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var end = text.length
    while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
    return text.substring(0, end) + "…"
}

private fun shareQrBitmap(context: Context, bitmap: Bitmap) {
    val qrDir = File(context.cacheDir, "qr")
    qrDir.mkdirs()
    qrDir.listFiles()?.filter { it.name.startsWith("share_qr") }?.forEach { it.delete() }
    val file = File(qrDir, "share_qr_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share QR Code"))
}
