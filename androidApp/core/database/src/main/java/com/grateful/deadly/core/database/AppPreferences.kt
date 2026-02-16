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
}
