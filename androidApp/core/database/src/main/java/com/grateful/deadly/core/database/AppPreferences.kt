package com.grateful.deadly.core.database

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide user preferences backed by SharedPreferences.
 *
 * Exposes reactive StateFlows so composables and repositories can
 * observe preference changes without polling.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SHOW_ONLY_RECORDED = "show_only_recorded_shows"
        private const val KEY_FAVORITES_DISPLAY_MODE = "favorites_display_mode"
        private const val KEY_FORCE_ONLINE = "force_online"
        // Legacy key kept for migration read-back
        private const val KEY_LIBRARY_DISPLAY_MODE_LEGACY = "library_display_mode"
    }

    private val _showOnlyRecordedShows = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_ONLY_RECORDED, true)
    )

    /** When true, shows with recordingCount == 0 are hidden from browse/search/nav. */
    val showOnlyRecordedShows: StateFlow<Boolean> = _showOnlyRecordedShows.asStateFlow()

    fun setShowOnlyRecordedShows(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ONLY_RECORDED, value).apply()
        _showOnlyRecordedShows.value = value
    }

    private val _favoritesDisplayMode = MutableStateFlow(
        prefs.getString(KEY_FAVORITES_DISPLAY_MODE, null)
            ?: prefs.getString(KEY_LIBRARY_DISPLAY_MODE_LEGACY, null)
            ?: "LIST"
    )

    /** Persisted grid/list display mode for the favorites screen ("LIST" or "GRID"). */
    val favoritesDisplayMode: StateFlow<String> = _favoritesDisplayMode.asStateFlow()

    fun setFavoritesDisplayMode(mode: String) {
        prefs.edit().putString(KEY_FAVORITES_DISPLAY_MODE, mode).apply()
        _favoritesDisplayMode.value = mode
    }

    private val _forceOnline = MutableStateFlow(
        prefs.getBoolean(KEY_FORCE_ONLINE, false)
    )

    /** When true, overrides offline detection and treats the app as online. */
    val forceOnline: StateFlow<Boolean> = _forceOnline.asStateFlow()

    fun setForceOnline(value: Boolean) {
        prefs.edit().putBoolean(KEY_FORCE_ONLINE, value).apply()
        _forceOnline.value = value
    }

}
