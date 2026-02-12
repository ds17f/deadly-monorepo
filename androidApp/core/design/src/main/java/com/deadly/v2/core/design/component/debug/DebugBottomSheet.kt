package com.deadly.v2.core.design.component.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.ContentPaste
//import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.BorderStroke
import com.deadly.v2.core.design.R

/**
 * Reusable debug bottom sheet component that displays structured debug information.
 * Only visible when debug mode is enabled in settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugBottomSheet(
    debugData: DebugData,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return
    
    val clipboardManager = LocalClipboardManager.current
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            // Custom drag handle with debug indicator
            Surface(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(width = 32.dp, height = 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = Color(0xFFFF5722) // Debug red color
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Header with title and copy all button
            DebugHeader(
                screenName = debugData.screenName,
                itemCount = debugData.getTotalItemCount(),
                onCopyAll = {
                    clipboardManager.setText(AnnotatedString(debugData.toFormattedText()))
                    DebugLogger.logDebugData(debugData, "COPY_ALL")
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Debug sections
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(debugData.sections) { section ->
                    DebugSection(
                        section = section,
                        screenName = debugData.screenName,
                        onCopySection = {
                            clipboardManager.setText(AnnotatedString(section.toFormattedText()))
                            DebugLogger.logDebugSection(section, debugData.screenName, "COPY_SECTION")
                        }
                    )
                }
            }
        }
    }
}

/**
 * Header component for the debug panel
 */
@Composable
private fun DebugHeader(
    screenName: String,
    itemCount: Int,
    onCopyAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "🐛 Debug Panel",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF5722)
            )
            Text(
                text = "$screenName • $itemCount items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Button(
            onClick = onCopyAll,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF5722),
                contentColor = Color.White
            ),
            modifier = Modifier.height(36.dp)
        ) {
            Text(
                text = "📋",
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copy All", fontSize = 12.sp)
        }
    }
}

/**
 * Individual debug section component
 */
@Composable
private fun DebugSection(
    section: DebugSection,
    screenName: String,
    onCopySection: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            // Section header
            Surface(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isExpanded) "▼" else "▶",
                            color = Color(0xFFFF5722),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFF5722).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${section.items.size}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = Color(0xFFFF5722),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = onCopySection,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text(
                            text = "📋",
                            fontSize = 14.sp,
                            color = Color(0xFFFF5722)
                        )
                    }
                }
            }
            
            // Section content
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    section.items.forEachIndexed { index, item ->
                        DebugItem(item = item)
                        if (index < section.items.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual debug item component
 */
@Composable
private fun DebugItem(item: DebugItem) {
    when (item) {
        is DebugItem.KeyValue -> {
            DebugKeyValueItem(key = item.key, value = item.value)
        }
        is DebugItem.BooleanValue -> {
            DebugBooleanItem(key = item.key, value = item.value)
        }
        is DebugItem.NumericValue -> {
            DebugKeyValueItem(key = item.key, value = "${item.value}${item.unit}")
        }
        is DebugItem.Multiline -> {
            DebugMultilineItem(key = item.key, value = item.value)
        }
        is DebugItem.Error -> {
            DebugErrorItem(error = item)
        }
        is DebugItem.Timestamp -> {
            DebugKeyValueItem(key = item.label, value = item.getFormattedTime())
        }
        is DebugItem.JsonData -> {
            // Special handling for scrollable logs
            if (item.key == "SCROLLABLE_LOGS") {
                val logs = if (item.json.isBlank()) emptyList() else item.json.split("\n")
                DebugScrollableLogItem(logs = logs)
            } else {
                DebugMultilineItem(key = item.key, value = item.json)
            }
        }
    }
}

/**
 * Key-value debug item
 */
@Composable
private fun DebugKeyValueItem(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$key:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Boolean debug item with visual indicator
 */
@Composable
private fun DebugBooleanItem(key: String, value: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$key:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(0.6f)
        ) {
            Text(
                text = if (value) "✅" else "❌",
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (value) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        }
    }
}

/**
 * Multiline debug item
 */
@Composable
private fun DebugMultilineItem(key: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$key:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(8.dp),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Error debug item with highlighting
 */
@Composable
private fun DebugErrorItem(error: DebugItem.Error) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🚨",
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "ERROR",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF44336)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFFF44336).copy(alpha = 0.1f),
            border = BorderStroke(1.dp, Color(0xFFF44336).copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF44336),
                    fontWeight = FontWeight.Medium
                )
                error.stackTrace?.let { stackTrace ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stackTrace,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Scrollable log item for debug panel - shows logs with auto-scroll and proper scrolling
 */
@Composable
private fun DebugScrollableLogItem(logs: List<String>) {
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new logs are added
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Live Service Logs:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp), // Fixed height for scrolling
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💡 Tap test buttons below to see live logs appear here!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = "• $log",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${logs.size} service calls • Auto-scrolls to latest",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Debug logger for V2 - simplified version for now
 */
private object DebugLogger {
    fun logDebugData(data: DebugData, action: String) {
        // Simplified logging for V2 - can be enhanced later
        println("DEBUG: $action on ${data.screenName} with ${data.getTotalItemCount()} items")
    }
    
    fun logDebugSection(section: DebugSection, screenName: String, action: String) {
        // Simplified logging for V2 - can be enhanced later  
        println("DEBUG: $action on $screenName section '${section.title}' with ${section.items.size} items")
    }
}