package com.grateful.deadly.core.network.genius.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeniusSearchResponse(
    val response: GeniusResponse
)

@Serializable
data class GeniusResponse(
    val hits: List<GeniusHit> = emptyList()
)

@Serializable
data class GeniusHit(
    val type: String = "",
    val result: GeniusSongResult? = null
)

@Serializable
data class GeniusSongResult(
    val id: Int = 0,
    @SerialName("full_title") val fullTitle: String = "",
    val title: String = "",
    val url: String = "",
    @SerialName("primary_artist") val primaryArtist: GeniusArtist? = null
)

@Serializable
data class GeniusArtist(
    val id: Int = 0,
    val name: String = ""
)
