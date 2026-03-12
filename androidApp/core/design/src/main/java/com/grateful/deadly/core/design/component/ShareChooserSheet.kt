package com.grateful.deadly.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.resources.IconResources

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareChooserSheet(
    attachImage: Boolean,
    onAttachImageChanged: (Boolean) -> Unit,
    onMessageShare: () -> Unit,
    onQrShare: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Share",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Message option
            ListItem(
                headlineContent = { Text("Message") },
                supportingContent = { Text("Text with link") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable {
                    onMessageShare()
                    onDismiss()
                }
            )

            // QR Code option
            ListItem(
                headlineContent = { Text("QR Code") },
                supportingContent = { Text("Scannable poster") },
                leadingContent = {
                    Icon(
                        painter = IconResources.Content.QrCode(),
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable {
                    onQrShare()
                    onDismiss()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Attach image checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAttachImageChanged(!attachImage) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = attachImage,
                    onCheckedChange = onAttachImageChanged
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Attach image",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Included with message",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
