package com.grateful.deadly.feature.search.screens.searchResults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.model.SearchResultShow
import com.grateful.deadly.feature.search.screens.main.models.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchShowActionsSheet(
    searchResult: SearchResultShow,
    viewModel: SearchViewModel,
    onDismiss: () -> Unit,
    onFavoriteToggled: (added: Boolean) -> Unit,
    onSeeReviews: () -> Unit = {}
) {
    val show = searchResult.show
    val isFavorite by viewModel.isShowFavorite(show.id).collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = "${show.date} • ${show.location.displayText}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = show.venue.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Add/Remove from Favorites
            if (isFavorite) {
                ListItem(
                    headlineContent = {
                        Text(
                            "Remove from Favorites",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = IconResources.Content.Favorite(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.removeFromFavorites(show.id)
                        onFavoriteToggled(false)
                    }
                )
            } else {
                ListItem(
                    headlineContent = { Text("Add to Favorites") },
                    leadingContent = {
                        Icon(
                            painter = IconResources.Content.FavoriteBorder(),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.addToFavorites(show.id)
                        onFavoriteToggled(true)
                    }
                )
            }

            // See Reviews (only if recording exists)
            if (show.bestRecordingId != null) {
                ListItem(
                    headlineContent = { Text("See Reviews") },
                    leadingContent = {
                        Icon(
                            painter = IconResources.Content.RateReview(),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable {
                        onSeeReviews()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
