package com.grateful.deadly.core.network.wikipedia.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WikipediaSummaryResponse(
    val title: String = "",
    val extract: String = "",
    @SerialName("extract_html") val extractHtml: String = "",
    val description: String? = null,
    val thumbnail: WikipediaThumbnail? = null
)

@Serializable
data class WikipediaThumbnail(
    val source: String = "",
    val width: Int = 0,
    val height: Int = 0
)
