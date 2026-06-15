package com.grateful.deadly.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.resources.IconResources

/**
 * Bottom sheet showing full show details (date, venue, location, rating)
 * in large readable text. Used on long-press of show cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowDetailBottomSheet(
    date: String,
    venue: String,
    location: String,
    rating: String?,
    onDismiss: () -> Unit,
    onAddToUpNext: (() -> Unit)? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = venue,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = location,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (rating != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = rating,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (onAddToUpNext != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Add to Up Next") },
                    leadingContent = {
                        Icon(
                            painter = IconResources.Content.PlaylistAdd(),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable { onAddToUpNext() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
