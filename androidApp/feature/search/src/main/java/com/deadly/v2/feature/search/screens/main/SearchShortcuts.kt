package com.deadly.v2.feature.search.screens.main

import androidx.annotation.DrawableRes

data class SearchShortcut(
    val title: String,
    val subtitle: String,
    val searchQuery: String,
    val priority: Int = 0,
    @DrawableRes val discoverImageRes: Int? = null,
    @DrawableRes val browseImageRes: Int? = null,
)

val allSearchShortcuts = listOf(
    // Filters (priority 10) — these use the new FTS tags
    SearchShortcut("Top Rated", "Highest rated recordings", "top-rated", 10),
    SearchShortcut("Popular", "Most reviewed shows", "popular", 10),
    SearchShortcut("Soundboard", "Direct from the mixing board", "sbd", 10),
    SearchShortcut("Audience", "Taped from the crowd", "aud", 10),

    // Venues (priority 5)
    SearchShortcut("Fillmore", "Fillmore East & West", "Fillmore", 5),
    SearchShortcut("Winterland", "San Francisco's legendary venue", "Winterland", 5),
    SearchShortcut("Red Rocks", "Morrison, Colorado", "Red Rocks", 5),
    SearchShortcut("MSG", "Madison Square Garden", "Madison Square Garden", 5),
    SearchShortcut("Capitol Theatre", "Port Chester, New York", "Capitol Theatre", 5),
    SearchShortcut("Barton Hall", "Cornell University, Ithaca", "Barton Hall", 5),

    // Cities (priority 5)
    SearchShortcut("New York", "The Big Apple", "New York", 5),
    SearchShortcut("San Francisco", "Home turf", "San Francisco", 5),
    SearchShortcut("Chicago", "The Windy City", "Chicago", 5),
    SearchShortcut("Philadelphia", "City of Brotherly Love", "Philadelphia", 5),
    SearchShortcut("Boston", "New England shows", "Boston", 5),
    SearchShortcut("Los Angeles", "SoCal shows", "Los Angeles", 5),

    // Songs (priority 5)
    SearchShortcut("Dark Star", "The quintessential jam", "Dark Star", 5),
    SearchShortcut("Scarlet > Fire", "The classic combo", "Scarlet Begonias", 5),
    SearchShortcut("Wharf Rat", "The ballad of August West", "Wharf Rat", 5),
    SearchShortcut("Truckin'", "What a long strange trip", "Truckin", 5),
    SearchShortcut("Eyes of the World", "Jazz-infused Garcia classic", "Eyes of the World", 5),
    SearchShortcut("Sugar Magnolia", "Sunshine daydream", "Sugar Magnolia", 5),

    // Members (priority 3)
    SearchShortcut("Brent Era", "Brent Mydland on keys", "Brent", 3),
    SearchShortcut("Pigpen Era", "Blues and soul frontman years", "Pigpen", 3),
    SearchShortcut("Keith Era", "Keith Godchaux on piano", "Keith", 3),
)
