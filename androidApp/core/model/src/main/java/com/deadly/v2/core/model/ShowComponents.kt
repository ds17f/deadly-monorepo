package com.deadly.v2.core.model

import kotlinx.serialization.Serializable

/**
 * V2 Show domain model components
 * 
 * Value objects that compose the Show domain model.
 */

@Serializable
data class Venue(
    val name: String,
    val city: String?,
    val state: String?,
    val country: String
) {
    val displayLocation: String
        get() = listOfNotNull(city, state, country.takeIf { it != "USA" })
            .joinToString(", ")
}

@Serializable
data class Location(
    val displayText: String,
    val city: String?,
    val state: String?
) {
    companion object {
        fun fromRaw(raw: String?, city: String?, state: String?): Location {
            val display = raw ?: listOfNotNull(city, state).joinToString(", ").ifEmpty { "Unknown Location" }
            return Location(display, city, state)
        }
    }
}

@Serializable
data class Setlist(
    val status: String,
    val sets: List<SetlistSet>,
    val raw: String?,
    val date: String? = null,
    val venue: String? = null
) {
    companion object {
        fun parse(json: String?, status: String?): Setlist? {
            if (json.isNullOrBlank() || status.isNullOrBlank()) return null
            
            return try {
                // Parse as array of sets: [{"set_name": "Set 1", "songs": [{"name": "...", "segue_into_next": true}]}]
                val jsonArray = org.json.JSONArray(json)
                val sets = mutableListOf<SetlistSet>()
                
                for (i in 0 until jsonArray.length()) {
                    val setObj = jsonArray.getJSONObject(i)
                    val setName = setObj.getString("set_name")
                    val songsArray = setObj.getJSONArray("songs")
                    val songs = mutableListOf<SetlistSong>()
                    
                    for (j in 0 until songsArray.length()) {
                        val songObj = songsArray.getJSONObject(j)
                        val songName = songObj.getString("name")
                        val segueIntoNext = songObj.optBoolean("segue_into_next", false)
                        
                        songs.add(
                            SetlistSong(
                                name = songName,
                                position = j + 1,
                                hasSegue = segueIntoNext,
                                segueSymbol = if (segueIntoNext) ">" else null
                            )
                        )
                    }
                    
                    sets.add(SetlistSet(setName, songs))
                }
                
                Setlist(
                    status = status,
                    sets = sets,
                    raw = json,
                    date = null, // Date info not in setlist JSON, comes from show
                    venue = null // Venue info not in setlist JSON, comes from show
                )
            } catch (e: Exception) {
                // If parsing fails, return null
                null
            }
        }
    }
}

@Serializable
data class SetlistSet(
    val name: String,
    val songs: List<SetlistSong>
)

@Serializable
data class SetlistSong(
    val name: String,
    val position: Int,
    val hasSegue: Boolean = false,
    val segueSymbol: String? = null
) {
    val displayName: String
        get() = if (hasSegue && segueSymbol != null) {
            "$name $segueSymbol"
        } else {
            name
        }
}

@Serializable
data class Lineup(
    val status: String,
    val members: List<LineupMember>,
    val raw: String?
) {
    companion object {
        fun parse(json: String?, status: String?): Lineup? {
            if (json.isNullOrBlank() || status.isNullOrBlank()) return null
            
            // For now, return a simple structure
            // TODO: Implement full JSON parsing when needed
            return Lineup(
                status = status,
                members = emptyList(),
                raw = json
            )
        }
    }
}

@Serializable
data class LineupMember(
    val name: String,
    val instruments: String
)