package com.grateful.deadly.feature.playlist.screens.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.design.component.ShareMenuRow

/**
 * Playlist menu bottom sheet that appears when triple dot menu is tapped.
 * Follows standard design patterns with Share and Choose Recording options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistMenuSheet(
    showDate: String?,
    venue: String?,
    location: String?,
    onShareClick: () -> Unit,
    onShowQrCode: () -> Unit,
    onChooseRecordingClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Share option
            ShareMenuRow(
                onClick = {
                    onShareClick()
                    onDismiss()
                }
            )

            // Show QR Code option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onShowQrCode()
                        onDismiss()
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = IconResources.Content.QrCode(),
                    contentDescription = "Show QR Code",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Show QR Code",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Choose Recording option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onChooseRecordingClick()
                        onDismiss()
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = IconResources.Content.LibraryMusic(),
                    contentDescription = "Choose Recording",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Choose Recording",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
