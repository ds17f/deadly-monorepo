package com.grateful.deadly.feature.settings.screens.bugreport

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log
import android.widget.Toast
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Send Bug Report" screen — the Android counterpart to iOS `BugReportView`.
 * Dumps the last hour of this process's logcat, shows a preview, and offers
 * Share (as a .txt attachment via FileProvider) + Copy + Refresh.
 *
 * Rendered as a local drill-down inside Settings (see [com.grateful.deadly.feature.settings.SettingsScreen]),
 * so it takes an [onBack] callback and a header bar rather than being a nav route.
 */
@Composable
fun BugReportScreen(
    onBack: () -> Unit,
    headerBar: @Composable (title: String, onBack: () -> Unit) -> Unit,
    viewModel: BugReportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var logText by remember { mutableStateOf("") }
    // Bounded tail for display only; [logText] (full log) is what Send/Copy/Share use.
    var preview by remember { mutableStateOf(BugReportExporter.LogPreview("", 0, 0, isTruncated = false)) }
    var isLoading by remember { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val sendState by viewModel.state.collectAsState()
    val isSending = sendState is BugReportSendState.Sending

    suspend fun reload() {
        isLoading = true
        copied = false
        viewModel.resetState()
        val (full, tail) = withContext(Dispatchers.IO) {
            val full = BugReportExporter.exportRecentLogs()
            full to BugReportExporter.previewTail(full)
        }
        logText = full
        preview = tail
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    Column(modifier = Modifier.fillMaxSize()) {
        headerBar("Send Bug Report", onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Text("Logs from the last hour", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap Send Bug Report to send these logs straight to support. They contain URLs " +
                    "of tracks you played and timing information — no account or personal data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("What happened? (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3
            )
        }

        HorizontalDivider()

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Reading logs…", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (preview.isTruncated) {
                        Text(
                            text = "Showing last ${preview.shownLines} of ${preview.totalLines} lines — " +
                                "full log is included when you send.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        )
                    }
                    // Only the bounded tail is rendered: a multi-MB Text exceeds
                    // Compose's max layout height and blocks the main thread.
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preview.text,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (val s = sendState) {
                is BugReportSendState.Success ->
                    StatusRow(Icons.Filled.Check, "Sent — thank you!", Color(0xFF2E7D32))
                is BugReportSendState.Failure ->
                    StatusRow(Icons.Filled.Error, s.message, MaterialTheme.colorScheme.error)
                else -> {}
            }

            Button(
                onClick = { viewModel.send(logText, note) },
                enabled = !isLoading && !isSending && logText.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isSending) "Sending…" else "Send Bug Report")
            }

            // Fallback path — for when sending fails or the user prefers to send
            // the logs themselves (e.g. via email).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val copiedAll = copyToClipboard(context, logText)
                        if (!copiedAll) {
                            Toast.makeText(
                                context,
                                "Log is too large to copy in full. Copied the most recent part. Use Share for the full log.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        copied = true
                        scope.launch { delay(1500); copied = false }
                    },
                    enabled = !isLoading && logText.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (copied) "Copied" else "Copy")
                }

                OutlinedButton(
                    onClick = { scope.launch { shareLogs(context, logText) } },
                    enabled = !isLoading && logText.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.IosShare,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }

                OutlinedButton(
                    onClick = { scope.launch { reload() } },
                    enabled = !isLoading && !isSending
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        }
    }
}

@Composable
private fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

/**
 * Copy the full log if it fits on the clipboard; otherwise copy its most recent
 * part. Returns true if the full log was copied.
 */
private fun copyToClipboard(context: Context, text: String): Boolean {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    fun setClip(value: String) = clipboard.setPrimaryClip(ClipData.newPlainText("Deadly bug report", value))
    val tail = { maxChars: Int -> BugReportExporter.previewTail(text, maxLines = Int.MAX_VALUE, maxChars = maxChars).text }

    if (text.length <= BugReportExporter.CLIPBOARD_MAX_CHARS) {
        try {
            setClip(text)
            return true
        } catch (e: RuntimeException) {
            // TransactionTooLargeException surfaces as a RuntimeException; fall back to the preview.
            Log.w("BugReportScreen", "Full log did not fit on the clipboard", e)
            setClip(tail(BugReportExporter.PREVIEW_MAX_CHARS))
            return false
        }
    }
    try {
        setClip(tail(BugReportExporter.CLIPBOARD_MAX_CHARS))
    } catch (e: RuntimeException) {
        Log.w("BugReportScreen", "Log tail did not fit on the clipboard", e)
        setClip(tail(BugReportExporter.PREVIEW_MAX_CHARS))
    }
    return false
}

private suspend fun shareLogs(context: Context, text: String) {
    // Writing a multi-MB file must not block the main thread.
    val file = withContext(Dispatchers.IO) { BugReportExporter.writeToCache(context, text) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Deadly bug report")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Send bug report"))
}
