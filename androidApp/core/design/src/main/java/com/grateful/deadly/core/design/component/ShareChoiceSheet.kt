package com.grateful.deadly.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.resources.IconResources

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareChoiceSheet(
    onShareViaApp: () -> Unit,
    onShareQrCode: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            ListItem(
                headlineContent = { Text("Share Link") },
                leadingContent = {
                    Icon(
                        painter = IconResources.Content.Share(),
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable {
                    onShareViaApp()
                    onDismiss()
                }
            )

            ListItem(
                headlineContent = { Text("Share QR Code") },
                leadingContent = {
                    Icon(
                        painter = IconResources.Content.QrCode(),
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable {
                    onShareQrCode()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
