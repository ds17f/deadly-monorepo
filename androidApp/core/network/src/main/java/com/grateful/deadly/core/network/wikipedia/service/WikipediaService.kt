package com.grateful.deadly.core.network.wikipedia.service

interface WikipediaService {
    suspend fun getVenueSummary(venueName: String, city: String?): String?
}
