package com.grateful.deadly.core.network.genius.service

interface GeniusService {
    suspend fun getLyrics(songTitle: String, artist: String): String?
}
