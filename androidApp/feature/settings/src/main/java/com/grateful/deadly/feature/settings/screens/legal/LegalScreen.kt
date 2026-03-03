package com.grateful.deadly.feature.settings.screens.legal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LegalScreen() {
    val context = LocalContext.current

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        item {
            ArticleSection(title = "Streaming & Recording Access Policy") {
                ArticleParagraph("This app streams live recordings from the Internet Archive, a non-profit digital library that hosts recordings in accordance with the Grateful Dead's long-standing non-commercial taping tradition and current rights-holder policies.")
                ArticleParagraph("This app is independent and not affiliated with the band, its members, or its management.")
            }
        }

        item {
            ArticleSection(title = "The Band's Taping & Sharing Tradition") {
                ArticleParagraph("The Grateful Dead historically permitted audience members to record live performances for personal, non-commercial use and free trading. This policy helped create one of the most active live-music communities in history.")
                Text(
                    text = "Historical policy statements:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ArticleLink(
                    label = "Grateful Dead Statement to Digital Archive Operators",
                    onClick = { openUrl("https://web.archive.org/web/20051124082136/http://www.sugarmegs.org/purpose.html") }
                )
                ArticleLink(
                    label = "WIRED: Everyone Is Grateful Again",
                    onClick = { openUrl("https://www.wired.com/2005/12/everyone-is-grateful-again/") }
                )
            }
        }

        item {
            ArticleSection(title = "Internet Archive Collection Policy") {
                ArticleParagraph("The Internet Archive hosts the Grateful Dead collection under specific access rules set in coordination with rights holders. Availability of recordings — including whether they are stream-only or downloadable — is determined by the Archive and applicable rights holders.")
                ArticleLink(
                    label = "Grateful Dead Collection Help Page",
                    onClick = { openUrl("https://archivesupport.zendesk.com/hc/en-us/articles/360004715891-The-Grateful-Dead-Collection") }
                )
            }
        }

        item {
            ArticleSection(title = "How This App Handles Streaming") {
                ArticleBullet("All recordings are streamed directly from the Internet Archive.")
                ArticleBullet("This app does not host, modify, or redistribute audio files.")
                ArticleBullet("Recording availability is subject to change based on Archive or rights-holder decisions.")
            }
        }

        item {
            ArticleSection(title = "Offline Listening") {
                ArticleParagraph("Where technically permitted by the Internet Archive, this app may allow recordings to be temporarily downloaded for in-app offline listening only.")
                ArticleBullet("Downloads are stored within the app.")
                ArticleBullet("They are not provided as exported audio files.")
                ArticleBullet("They are intended solely for personal, non-commercial listening.")
                ArticleParagraph("Users are responsible for complying with applicable copyright and Archive usage policies.")
            }
        }

        item {
            ArticleSection(title = "Official Commercial Releases") {
                ArticleParagraph("Commercially released recordings — studio albums, official live releases, box sets, and similar material — are protected by copyright and should be accessed through authorized services.")
            }
        }

        item {
            ArticleSection(title = "Respect for Artists & Rights Holders") {
                ArticleParagraph("This app exists to support listening, exploration, and historical appreciation of the Grateful Dead's live performances — in the spirit of their taping tradition — while respecting modern copyright law and the policies of the Internet Archive and rights holders.")
            }
        }
    }
}

@Composable
private fun ArticleSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun ArticleParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        lineHeight = 28.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ArticleBullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 28.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ArticleLink(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = "↗ $label",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
