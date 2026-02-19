package com.grateful.deadly.feature.settings.screens.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grateful.deadly.feature.settings.BuildConfig

@Composable
fun AboutScreen() {
    val context = LocalContext.current

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Deadly",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Support the Archive
        item {
            AboutSection(title = "Support the Archive") {
                Text(
                    text = "The Internet Archive is the backbone of this app. Their infrastructure hosts and streams every recording you hear. Please consider donating directly to help cover their hosting and bandwidth costs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = { openUrl("https://archive.org/donate/") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Donate to Internet Archive")
                }
            }
        }

        // Our Mission
        item {
            AboutSection(title = "Our Mission") {
                AboutParagraph("We built this app for one simple reason: we want to encourage Deadheads — old and new — to engage with, enjoy, and share the music of the Grateful Dead.")
                AboutParagraph("The goal is to make listening to live shows as easy and enjoyable as possible in a modern streaming experience. We have deep respect for the spirit of the band and the long-standing belief that this music is meant to be shared — freely, non-commercially, and in community.")
                AboutParagraph("This app is completely open source. Anyone can inspect the code, contribute improvements, or build upon it.")
                AboutParagraph("No money is made from streaming music through this app. It exists because one Deadhead wanted a modern way to listen to his favorite band.")
            }
        }

        // Streaming & Recording Access Policy
        item {
            AboutSection(title = "Streaming & Recording Access Policy") {
                AboutParagraph("This app streams live recordings from the Internet Archive, a non-profit digital library that hosts recordings in accordance with the Grateful Dead's long-standing non-commercial taping tradition and current rights-holder policies.")
                AboutParagraph("This app is independent and not affiliated with the band, its members, or its management.")
            }
        }

        // The Band's Taping & Sharing Tradition
        item {
            AboutSection(title = "The Band's Taping & Sharing Tradition") {
                AboutParagraph("The Grateful Dead historically permitted audience members to record live performances for personal, non-commercial use and free trading. This policy helped create one of the most active live-music communities in history.")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Historical policy statements:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                AboutLink(
                    label = "Grateful Dead Statement to Digital Archive Operators",
                    url = "https://web.archive.org/web/20051124082136/http://www.sugarmegs.org/purpose.html",
                    onClick = { openUrl("https://web.archive.org/web/20051124082136/http://www.sugarmegs.org/purpose.html") }
                )
                AboutLink(
                    label = "WIRED: Everyone Is Grateful Again (Dennis McNally on tape trading)",
                    url = "https://www.wired.com/2005/12/everyone-is-grateful-again/",
                    onClick = { openUrl("https://www.wired.com/2005/12/everyone-is-grateful-again/") }
                )
            }
        }

        // Internet Archive Collection Policy
        item {
            AboutSection(title = "Internet Archive Collection Policy") {
                AboutParagraph("The Internet Archive hosts the Grateful Dead collection under specific access rules set in coordination with rights holders. Availability of recordings — including whether they are stream-only or downloadable — is determined by the Archive and applicable rights holders.")
                AboutLink(
                    label = "Grateful Dead Collection Help Page",
                    url = "https://archivesupport.zendesk.com/hc/en-us/articles/360004715891",
                    onClick = { openUrl("https://archivesupport.zendesk.com/hc/en-us/articles/360004715891-The-Grateful-Dead-Collection") }
                )
            }
        }

        // How This App Handles Streaming
        item {
            AboutSection(title = "How This App Handles Streaming") {
                AboutBullet("All recordings are streamed directly from the Internet Archive.")
                AboutBullet("This app does not host, modify, or redistribute audio files.")
                AboutBullet("Recording availability is subject to change based on Archive or rights-holder decisions.")
            }
        }

        // Offline Listening
        item {
            AboutSection(title = "Offline Listening") {
                AboutParagraph("Where technically permitted by the Internet Archive, this app may allow recordings to be temporarily downloaded for in-app offline listening only.")
                AboutBullet("Downloads are stored within the app.")
                AboutBullet("They are not provided as exported audio files.")
                AboutBullet("They are intended solely for personal, non-commercial listening.")
                Spacer(modifier = Modifier.height(4.dp))
                AboutParagraph("Users are responsible for complying with applicable copyright and Archive usage policies.")
            }
        }

        // Official Commercial Releases
        item {
            AboutSection(title = "Official Commercial Releases") {
                AboutParagraph("Commercially released recordings — studio albums, official live releases, box sets, and similar material — are protected by copyright and should be accessed through authorized services.")
            }
        }

        // Respect for Artists & Rights Holders
        item {
            AboutSection(title = "Respect for Artists & Rights Holders") {
                AboutParagraph("This app exists to support listening, exploration, and historical appreciation of the Grateful Dead's live performances — in the spirit of their taping tradition — while respecting modern copyright law and the policies of the Internet Archive and rights holders.")
            }
        }
    }
}

@Composable
private fun AboutSection(
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
private fun AboutParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun AboutBullet(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
private fun AboutLink(
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
