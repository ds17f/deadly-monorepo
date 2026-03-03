package com.grateful.deadly.feature.settings.screens.legal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LegalScreen() {
    val context = LocalContext.current

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            LegalSection(title = "Streaming & Recording Access Policy") {
                LegalParagraph("This app streams live recordings from the Internet Archive, a non-profit digital library that hosts recordings in accordance with the Grateful Dead's long-standing non-commercial taping tradition and current rights-holder policies.")
                LegalParagraph("This app is independent and not affiliated with the band, its members, or its management.")
            }
        }

        item {
            LegalSection(title = "The Band's Taping & Sharing Tradition") {
                LegalParagraph("The Grateful Dead historically permitted audience members to record live performances for personal, non-commercial use and free trading. This policy helped create one of the most active live-music communities in history.")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Historical policy statements:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LegalLink(
                    label = "Grateful Dead Statement to Digital Archive Operators",
                    url = "https://web.archive.org/web/20051124082136/http://www.sugarmegs.org/purpose.html",
                    onClick = { openUrl("https://web.archive.org/web/20051124082136/http://www.sugarmegs.org/purpose.html") }
                )
                LegalLink(
                    label = "WIRED: Everyone Is Grateful Again (Dennis McNally on tape trading)",
                    url = "https://www.wired.com/2005/12/everyone-is-grateful-again/",
                    onClick = { openUrl("https://www.wired.com/2005/12/everyone-is-grateful-again/") }
                )
            }
        }

        item {
            LegalSection(title = "Internet Archive Collection Policy") {
                LegalParagraph("The Internet Archive hosts the Grateful Dead collection under specific access rules set in coordination with rights holders. Availability of recordings — including whether they are stream-only or downloadable — is determined by the Archive and applicable rights holders.")
                LegalLink(
                    label = "Grateful Dead Collection Help Page",
                    url = "https://archivesupport.zendesk.com/hc/en-us/articles/360004715891",
                    onClick = { openUrl("https://archivesupport.zendesk.com/hc/en-us/articles/360004715891-The-Grateful-Dead-Collection") }
                )
            }
        }

        item {
            LegalSection(title = "How This App Handles Streaming") {
                LegalBullet("All recordings are streamed directly from the Internet Archive.")
                LegalBullet("This app does not host, modify, or redistribute audio files.")
                LegalBullet("Recording availability is subject to change based on Archive or rights-holder decisions.")
            }
        }

        item {
            LegalSection(title = "Offline Listening") {
                LegalParagraph("Where technically permitted by the Internet Archive, this app may allow recordings to be temporarily downloaded for in-app offline listening only.")
                LegalBullet("Downloads are stored within the app.")
                LegalBullet("They are not provided as exported audio files.")
                LegalBullet("They are intended solely for personal, non-commercial listening.")
                Spacer(modifier = Modifier.height(4.dp))
                LegalParagraph("Users are responsible for complying with applicable copyright and Archive usage policies.")
            }
        }

        item {
            LegalSection(title = "Official Commercial Releases") {
                LegalParagraph("Commercially released recordings — studio albums, official live releases, box sets, and similar material — are protected by copyright and should be accessed through authorized services.")
            }
        }

        item {
            LegalSection(title = "Respect for Artists & Rights Holders") {
                LegalParagraph("This app exists to support listening, exploration, and historical appreciation of the Grateful Dead's live performances — in the spirit of their taping tradition — while respecting modern copyright law and the policies of the Internet Archive and rights holders.")
            }
        }
    }
}

@Composable
private fun LegalSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun LegalParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun LegalBullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LegalLink(
    label: String,
    url: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
