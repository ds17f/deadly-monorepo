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
import com.grateful.deadly.core.model.ShowArtworkService
import com.grateful.deadly.core.model.SourceBadgeStyle
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
        private const val KEY_INCLUDE_SHOWS_WITHOUT_RECORDINGS = "include_shows_without_recordings"
        private const val KEY_FAVORITES_DISPLAY_MODE = "favorites_display_mode"
        private const val KEY_FORCE_ONLINE = "force_online"
        // Legacy key kept for migration read-back
        private const val KEY_LIBRARY_DISPLAY_MODE_LEGACY = "library_display_mode"
        // Equalizer
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_EQ_PRESET = "eq_preset"
        private const val KEY_EQ_BAND_LEVELS = "eq_band_levels"
        private const val KEY_SHARE_ATTACH_IMAGE = "share_attach_image"
        private const val KEY_SOURCE_BADGE_STYLE = "source_badge_style"
    }

    private val _includeShowsWithoutRecordings = MutableStateFlow(
        prefs.getBoolean(KEY_INCLUDE_SHOWS_WITHOUT_RECORDINGS, false)
    )

    /** When true, shows with recordingCount == 0 are included in browse/search/nav. */
    val includeShowsWithoutRecordings: StateFlow<Boolean> = _includeShowsWithoutRecordings.asStateFlow()

    fun setIncludeShowsWithoutRecordings(value: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_SHOWS_WITHOUT_RECORDINGS, value).apply()
        _includeShowsWithoutRecordings.value = value
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

    // ── Equalizer ────────────────────────────────────────────────────────

    private val _eqEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_EQ_ENABLED, false)
    )

    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    fun setEqEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_EQ_ENABLED, value).apply()
        _eqEnabled.value = value
    }

    private val _eqPreset = MutableStateFlow(
        prefs.getString(KEY_EQ_PRESET, null)
    )

    val eqPreset: StateFlow<String?> = _eqPreset.asStateFlow()

    fun setEqPreset(value: String?) {
        prefs.edit().putString(KEY_EQ_PRESET, value).apply()
        _eqPreset.value = value
    }

    private val _eqBandLevels = MutableStateFlow(
        prefs.getString(KEY_EQ_BAND_LEVELS, null)
    )

    /** Comma-separated band gains in dB (e.g. "0,0,0,0,0,0,0,0,0,0"). */
    val eqBandLevels: StateFlow<String?> = _eqBandLevels.asStateFlow()

    fun setEqBandLevels(value: String?) {
        prefs.edit().putString(KEY_EQ_BAND_LEVELS, value).apply()
        _eqBandLevels.value = value
    }

    // ── Share ─────────────────────────────────────────────────────────────

    private val _shareAttachImage = MutableStateFlow(
        prefs.getBoolean(KEY_SHARE_ATTACH_IMAGE, false)
    )

    /** When true, the "Message" share option includes a share card image. */
    val shareAttachImage: StateFlow<Boolean> = _shareAttachImage.asStateFlow()

    fun setShareAttachImage(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHARE_ATTACH_IMAGE, value).apply()
        _shareAttachImage.value = value
    }

    // ── Source Badge ──────────────────────────────────────────────────────

    private val _sourceBadgeStyle = MutableStateFlow(
        (prefs.getString(KEY_SOURCE_BADGE_STYLE, null) ?: "LONG").also {
            ShowArtworkService.badgeStyle = SourceBadgeStyle.fromString(it)
        }
    )

    /** Badge style on artwork thumbnails: SHORT (S/A), LONG (SBD/AUD), or ICON. */
    val sourceBadgeStyle: StateFlow<String> = _sourceBadgeStyle.asStateFlow()

    fun setSourceBadgeStyle(value: String) {
        prefs.edit().putString(KEY_SOURCE_BADGE_STYLE, value).apply()
        _sourceBadgeStyle.value = value
        ShowArtworkService.badgeStyle = SourceBadgeStyle.fromString(value)
    }

}
