package com.deadly.v2.core.database.mappers

import com.deadly.v2.core.database.entities.ShowEntity
import com.deadly.v2.core.database.entities.RecordingEntity
import com.deadly.v2.core.model.*
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Comprehensive tests for ShowMappers covering:
 * - Valid JSON parsing for setlist, lineup, recordings
 * - Null/empty JSON handling (returns empty lists)
 * - Malformed JSON handling (returns empty lists, no crashes)
 * - Complete entity-to-domain conversion accuracy
 * - List conversion accuracy
 * - RecordingSourceType.fromString() with various inputs
 */
class ShowMappersTest {
    
    private lateinit var showMappers: ShowMappers
    private lateinit var json: Json
    
    @Before
    fun setUp() {
        json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        showMappers = ShowMappers(json)
    }
    
    // Test entity creation helpers
    private fun createTestShowEntity(
        showId: String = "1977-05-08-test-show",
        date: String = "1977-05-08",
        year: Int = 1977,
        venueName: String = "Test Venue",
        city: String = "Test City",
        state: String = "NY",
        recordingsRaw: String? = """["rec1", "rec2"]""",
        setlistRaw: String? = null,
        lineupRaw: String? = null
    ) = ShowEntity(
        showId = showId,
        date = date,
        year = year,
        month = 5,
        yearMonth = "1977-05",
        band = "Grateful Dead",
        url = "http://test.com",
        venueName = venueName,
        city = city,
        state = state,
        country = "USA",
        locationRaw = "$city, $state",
        setlistStatus = "found",
        setlistRaw = setlistRaw,
        songList = "Scarlet Begonias,Fire on the Mountain",
        lineupStatus = "found", 
        lineupRaw = lineupRaw,
        memberList = "Jerry Garcia,Bob Weir",
        showSequence = 1,
        recordingsRaw = recordingsRaw,
        recordingCount = 2,
        bestRecordingId = "rec1",
        averageRating = 4.5f,
        totalReviews = 100,
        isInLibrary = true,
        libraryAddedAt = 1000L,
        createdAt = 2000L,
        updatedAt = 3000L
    )
    
    private fun createTestRecordingEntity(
        identifier: String = "test-recording-id",
        showId: String = "1977-05-08-test-show",
        sourceType: String = "SBD",
        rating: Double = 4.5,
        reviewCount: Int = 100
    ) = RecordingEntity(
        identifier = identifier,
        showId = showId,
        sourceType = sourceType,
        rating = rating,
        reviewCount = reviewCount
    )
    
    @Test
    fun `entityToDomain converts ShowEntity to Show correctly`() {
        val entity = createTestShowEntity()
        
        val result = showMappers.entityToDomain(entity)
        
        assertEquals(entity.showId, result.id)
        assertEquals(entity.date, result.date)
        assertEquals(entity.year, result.year)
        assertEquals(entity.band, result.band)
        assertEquals(entity.venueName, result.venue.name)
        assertEquals(entity.city, result.venue.city)
        assertEquals(entity.state, result.venue.state)
        assertEquals(entity.country, result.venue.country)
        assertEquals(entity.recordingCount, result.recordingCount)
        assertEquals(entity.bestRecordingId, result.bestRecordingId)
        assertEquals(entity.averageRating, result.averageRating)
        assertEquals(entity.totalReviews, result.totalReviews)
        assertEquals(entity.isInLibrary, result.isInLibrary)
        assertEquals(entity.libraryAddedAt, result.libraryAddedAt)
    }
    
    @Test
    fun `entityToDomain handles valid recording IDs JSON`() {
        val validJson = """["recording1", "recording2", "recording3"]"""
        val entity = createTestShowEntity(recordingsRaw = validJson)
        
        val result = showMappers.entityToDomain(entity)
        
        assertEquals(listOf("recording1", "recording2", "recording3"), result.recordingIds)
    }
    
    @Test
    fun `entityToDomain handles null recording IDs JSON`() {
        val entity = createTestShowEntity(recordingsRaw = null)
        
        val result = showMappers.entityToDomain(entity)
        
        assertEquals(emptyList<String>(), result.recordingIds)
    }
    
    @Test
    fun `entityToDomain handles empty recording IDs JSON`() {
        val entity = createTestShowEntity(recordingsRaw = "")
        
        val result = showMappers.entityToDomain(entity)
        
        assertEquals(emptyList<String>(), result.recordingIds)
    }
    
    @Test
    fun `entityToDomain handles malformed recording IDs JSON without crashing`() {
        val malformedJson = """{"this": "is not a list"}"""
        val entity = createTestShowEntity(recordingsRaw = malformedJson)
        
        val result = showMappers.entityToDomain(entity)
        
        // Should not crash and should return empty list
        assertEquals(emptyList<String>(), result.recordingIds)
    }
    
    @Test
    fun `entityToDomain handles invalid recording IDs JSON without crashing`() {
        val invalidJson = """[invalid json"""
        val entity = createTestShowEntity(recordingsRaw = invalidJson)
        
        val result = showMappers.entityToDomain(entity)
        
        // Should not crash and should return empty list
        assertEquals(emptyList<String>(), result.recordingIds)
    }
    
    @Test
    fun `entitiesToDomain converts list of ShowEntity to list of Show`() {
        val entities = listOf(
            createTestShowEntity("show1", "1977-05-07"),
            createTestShowEntity("show2", "1977-05-08"),
            createTestShowEntity("show3", "1977-05-09")
        )
        
        val result = showMappers.entitiesToDomain(entities)
        
        assertEquals(3, result.size)
        assertEquals("show1", result[0].id)
        assertEquals("show2", result[1].id)
        assertEquals("show3", result[2].id)
        assertEquals("1977-05-07", result[0].date)
        assertEquals("1977-05-08", result[1].date)
        assertEquals("1977-05-09", result[2].date)
    }
    
    @Test
    fun `entitiesToDomain handles empty list`() {
        val result = showMappers.entitiesToDomain(emptyList())
        
        assertEquals(emptyList<Show>(), result)
    }
    
    @Test
    fun `recordingEntityToDomain converts RecordingEntity to Recording correctly`() {
        val entity = createTestRecordingEntity()
        
        val result = showMappers.recordingEntityToDomain(entity)
        
        assertEquals(entity.identifier, result.identifier)
        assertEquals(entity.showId, result.showId)
        assertEquals(RecordingSourceType.SOUNDBOARD, result.sourceType)
        assertEquals(entity.rating, result.rating, 0.0)
        assertEquals(entity.reviewCount, result.reviewCount)
    }
    
    @Test
    fun `recordingEntitiesToDomain converts list of RecordingEntity to list of Recording`() {
        val entities = listOf(
            createTestRecordingEntity("rec1", sourceType = "SBD"),
            createTestRecordingEntity("rec2", sourceType = "AUD"),
            createTestRecordingEntity("rec3", sourceType = "MATRIX")
        )
        
        val result = showMappers.recordingEntitiesToDomain(entities)
        
        assertEquals(3, result.size)
        assertEquals("rec1", result[0].identifier)
        assertEquals("rec2", result[1].identifier)
        assertEquals("rec3", result[2].identifier)
        assertEquals(RecordingSourceType.SOUNDBOARD, result[0].sourceType)
        assertEquals(RecordingSourceType.AUDIENCE, result[1].sourceType)
        assertEquals(RecordingSourceType.MATRIX, result[2].sourceType)
    }
    
    @Test
    fun `recordingEntitiesToDomain handles empty list`() {
        val result = showMappers.recordingEntitiesToDomain(emptyList())
        
        assertEquals(emptyList<Recording>(), result)
    }
    
    @Test
    fun `RecordingSourceType fromString handles all valid values`() {
        assertEquals(RecordingSourceType.SOUNDBOARD, RecordingSourceType.fromString("SBD"))
        assertEquals(RecordingSourceType.SOUNDBOARD, RecordingSourceType.fromString("SOUNDBOARD"))
        assertEquals(RecordingSourceType.SOUNDBOARD, RecordingSourceType.fromString("sbd"))
        
        assertEquals(RecordingSourceType.AUDIENCE, RecordingSourceType.fromString("AUD"))
        assertEquals(RecordingSourceType.AUDIENCE, RecordingSourceType.fromString("AUDIENCE"))
        assertEquals(RecordingSourceType.AUDIENCE, RecordingSourceType.fromString("aud"))
        
        assertEquals(RecordingSourceType.FM, RecordingSourceType.fromString("FM"))
        assertEquals(RecordingSourceType.FM, RecordingSourceType.fromString("fm"))
        
        assertEquals(RecordingSourceType.MATRIX, RecordingSourceType.fromString("MATRIX"))
        assertEquals(RecordingSourceType.MATRIX, RecordingSourceType.fromString("MTX"))
        assertEquals(RecordingSourceType.MATRIX, RecordingSourceType.fromString("matrix"))
        
        assertEquals(RecordingSourceType.REMASTER, RecordingSourceType.fromString("REMASTER"))
        assertEquals(RecordingSourceType.REMASTER, RecordingSourceType.fromString("remaster"))
    }
    
    @Test
    fun `RecordingSourceType fromString handles invalid values with UNKNOWN`() {
        assertEquals(RecordingSourceType.UNKNOWN, RecordingSourceType.fromString(null))
        assertEquals(RecordingSourceType.UNKNOWN, RecordingSourceType.fromString(""))
        assertEquals(RecordingSourceType.UNKNOWN, RecordingSourceType.fromString("INVALID"))
        assertEquals(RecordingSourceType.UNKNOWN, RecordingSourceType.fromString("xyz"))
        assertEquals(RecordingSourceType.UNKNOWN, RecordingSourceType.fromString("123"))
    }
    
    @Test
    fun `entityToDomain handles setlist and lineup parsing`() {
        val entity = createTestShowEntity(
            setlistRaw = """{"sets": [{"name": "Set 1", "songs": ["Song 1", "Song 2"]}]}""",
            lineupRaw = """{"members": [{"name": "Jerry Garcia", "instruments": "Guitar"}]}"""
        )
        
        val result = showMappers.entityToDomain(entity)
        
        // These should not be null even if parsing fails - Setlist.parse handles gracefully
        // The exact structure depends on Setlist.parse implementation
        assertNotNull(result.setlist)
        assertNotNull(result.lineup)
    }
    
    @Test
    fun `entityToDomain handles malformed setlist and lineup JSON without crashing`() {
        val entity = createTestShowEntity(
            setlistRaw = """{"invalid": json""",
            lineupRaw = """[missing closing bracket"""
        )
        
        val result = showMappers.entityToDomain(entity)
        
        // Should not crash - Setlist.parse and Lineup.parse handle errors gracefully
        // Result depends on their implementation, but should not throw
        assertNotNull(result) // Basic test that conversion completed
    }
    
    @Test
    fun `Recording domain model computed properties work correctly`() {
        val entity = createTestRecordingEntity(rating = 4.5, reviewCount = 100)
        val result = showMappers.recordingEntityToDomain(entity)
        
        assertTrue(result.hasRating)
        assertEquals("4.5/5.0 (100 reviews)", result.displayRating)
        assertEquals("SBD • 4.5/5.0 (100 reviews)", result.displayTitle)
    }
    
    @Test
    fun `Recording domain model handles no rating correctly`() {
        val entity = createTestRecordingEntity(rating = 0.0, reviewCount = 0)
        val result = showMappers.recordingEntityToDomain(entity)
        
        assertFalse(result.hasRating)
        assertEquals("Not Rated", result.displayRating)
        assertEquals("SBD • Not Rated", result.displayTitle)
    }
}