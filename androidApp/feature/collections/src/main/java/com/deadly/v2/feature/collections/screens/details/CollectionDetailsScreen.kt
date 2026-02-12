package com.deadly.v2.feature.collections.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
// Note: Simplified imports for basic functionality
import com.deadly.v2.feature.collections.screens.details.models.CollectionDetailsUiState
import com.deadly.v2.feature.collections.screens.details.models.CollectionDetailsViewModel

/**
 * Collection details screen showing collection information and shows
 * 
 * Displays detailed information about a specific collection including:
 * - Collection metadata (name, description, tags)  
 * - List of shows in the collection
 * - Debug information panel
 */
@Composable
fun CollectionDetailsScreen(
    collectionId: String,
    highlightedShowId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToShow: (String) -> Unit,
    viewModel: CollectionDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Simplified UI without AppScaffold for now
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        when (val state = uiState) {
            is CollectionDetailsUiState.Loading -> {
                LoadingContent()
            }
            is CollectionDetailsUiState.Success -> {
                SuccessContent(
                    state = state,
                    onNavigateToShow = onNavigateToShow
                )
            }
            is CollectionDetailsUiState.Error -> {
                ErrorContent(error = state.message)
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Loading collection...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SuccessContent(
    state: CollectionDetailsUiState.Success,
    onNavigateToShow: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Collection header
        item {
            CollectionHeader(
                name = state.collection.name,
                description = state.collection.description,
                totalShows = state.collection.shows.size,
                tags = state.collection.tags
            )
        }
        
        // Shows section
        if (state.collection.shows.isNotEmpty()) {
            item {
                Text(
                    text = "Shows (${state.collection.shows.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(
                items = state.collection.shows,
                key = { it.id }
            ) { show ->
                ShowCard(
                    show = show,
                    onClick = { onNavigateToShow(show.id) }
                )
            }
        } else {
            item {
                Text(
                    text = "No shows available in this collection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    error: String
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error loading collection",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun CollectionHeader(
    name: String,
    description: String,
    totalShows: Int,
    tags: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            Text(
                text = "$totalShows shows",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            if (tags.isNotEmpty()) {
                Text(
                    text = "Tags: ${tags.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ShowCard(
    show: com.deadly.v2.core.model.Show,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "${show.date} - ${show.venue?.name ?: "Unknown Venue"}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            show.venue?.let { venue ->
                Text(
                    text = "${venue.city}, ${venue.state}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            
            show.averageRating?.let { rating ->
                Text(
                    text = "Rating: ${String.format("%.1f", rating)} (${show.totalReviews} reviews)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}