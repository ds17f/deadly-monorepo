package com.grateful.deadly.notifications

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration

// Render a notification body with tappable links (decision D). Supports
// markdown links `[label](url)` AND bare http(s) URLs, restricted to the
// http/https schemes. Built as an AnnotatedString with LinkAnnotation.Url so
// Compose Text handles taps (opening the system browser) — no manual gesture
// wiring, and no HTML injection surface.

// `[label](url)` OR a bare http(s):// URL. Markdown alt first so a URL already
// inside a markdown link isn't double-matched.
private val TOKEN =
    Regex("""\[([^\]]+)\]\((https?://[^\s)]+)\)|(https?://[^\s]+)""")

private val TRAILING = Regex("""[).,!?;:]+$""")

fun notificationBody(body: String, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        val styles = TextLinkStyles(
            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
        )
        var last = 0
        for (match in TOKEN.findAll(body)) {
            if (match.range.first > last) append(body.substring(last, match.range.first))
            val mdLabel = match.groupValues[1]
            val mdUrl = match.groupValues[2]
            val bareUrl = match.groupValues[3]
            when {
                mdUrl.isNotEmpty() -> {
                    withLink(LinkAnnotation.Url(mdUrl, styles)) { append(mdLabel) }
                }
                bareUrl.isNotEmpty() -> {
                    val trailing = TRAILING.find(bareUrl)?.value ?: ""
                    val url = bareUrl.dropLast(trailing.length)
                    withLink(LinkAnnotation.Url(url, styles)) { append(url) }
                    if (trailing.isNotEmpty()) append(trailing)
                }
                else -> append(match.value)
            }
            last = match.range.last + 1
        }
        if (last < body.length) append(body.substring(last))
    }
