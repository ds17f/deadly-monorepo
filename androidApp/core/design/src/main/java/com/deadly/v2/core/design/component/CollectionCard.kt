package com.deadly.v2.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.model.DeadCollection

/**
 * Format collection name with natural line breaks for better display
 */
private fun formatCollectionName(name: String): String {
    // Break at natural points: ":", "(", or after "Picks"
    return when {
        name.contains(": ") -> name.replace(": ", ":\n")
        name.contains(" (") -> name.replace(" (", "\n(")
        name.contains("Picks ") -> name.replace("Picks ", "Picks\n")
        else -> name
    }
}

/**
 * CollectionCard - Polaroid-style card for displaying collection information
 * 
 * Features a polaroid-like design with:
 * - Image placeholder area at top
 * - Caption area below with collection name and description
 * - Material3 color scheme integration
 */
@Composable
fun CollectionCard(
    collection: DeadCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDescription: Boolean = true
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Image placeholder area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📸",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Caption area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = formatCollectionName(collection.name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                if (showDescription) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = collection.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
