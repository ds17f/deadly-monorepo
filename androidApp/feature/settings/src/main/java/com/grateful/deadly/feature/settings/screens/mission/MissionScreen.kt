package com.grateful.deadly.feature.settings.screens.mission

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MissionScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "We built this app for one simple reason: we want to encourage Deadheads — old and new — to engage with, enjoy, and share the music of the Grateful Dead.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            Text(
                text = "The goal is to make listening to live shows as easy and enjoyable as possible in a modern streaming experience. We have deep respect for the spirit of the band and the long-standing belief that this music is meant to be shared — freely, non-commercially, and in community.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            Text(
                text = "This app is completely open source. Anyone can inspect the code, contribute improvements, or build upon it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            Text(
                text = "No money is made from streaming music through this app. It exists because one Deadhead wanted a modern way to listen to his favorite band.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
