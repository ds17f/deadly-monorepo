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
        private const val KEY_USE_BETA_SHARE_LINKS = "use_beta_share_links"
        private const val KEY_USE_BETA_MODE = "use_beta_mode"
        private const val KEY_SERVER_ENVIRONMENT = "server_environment"
        private const val KEY_CUSTOM_SERVER_URL = "custom_server_url"
        private const val KEY_CUSTOM_DEV_EMAIL = "custom_dev_email"
        private const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
        private const val KEY_INSTALL_ID = "install_id"
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

    // ── Analytics ──────────────────────────────────────────────────────

    private val _analyticsEnabled = MutableStateFlow(
        if (prefs.contains(KEY_ANALYTICS_ENABLED)) prefs.getBoolean(KEY_ANALYTICS_ENABLED, true) else true
    )

    /** When true, anonymous usage analytics are collected and sent. */
    val analyticsEnabled: StateFlow<Boolean> = _analyticsEnabled.asStateFlow()

    fun setAnalyticsEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ANALYTICS_ENABLED, value).apply()
        _analyticsEnabled.value = value
    }

    /** Persistent install ID (UUID). Generated once on first access, survives opt-out/opt-in cycles. */
    val installId: String = run {
        val existing = prefs.getString(KEY_INSTALL_ID, null)
        if (!existing.isNullOrEmpty()) {
            existing
        } else {
            val newId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALL_ID, newId).apply()
            newId
        }
    }

    // ── Server Environment ───────────────────────────────────────────

    private val _serverEnvironment: MutableStateFlow<String>
    private val _customServerUrl: MutableStateFlow<String>
    private val _customDevEmail: MutableStateFlow<String>

    init {
        // Migrate: read new key first, fall back to legacy beta mode keys
        val env = if (prefs.contains(KEY_SERVER_ENVIRONMENT)) {
            prefs.getString(KEY_SERVER_ENVIRONMENT, "prod")!!
        } else if (prefs.contains(KEY_USE_BETA_MODE)) {
            if (prefs.getBoolean(KEY_USE_BETA_MODE, false)) "beta" else "prod"
        } else {
            if (prefs.getBoolean(KEY_USE_BETA_SHARE_LINKS, false)) "beta" else "prod"
        }
        _serverEnvironment = MutableStateFlow(env)
        _customServerUrl = MutableStateFlow(prefs.getString(KEY_CUSTOM_SERVER_URL, "") ?: "")
        _customDevEmail = MutableStateFlow(prefs.getString(KEY_CUSTOM_DEV_EMAIL, "") ?: "")
    }

    /** Server environment: "prod", "beta", or "custom". */
    val serverEnvironment: StateFlow<String> = _serverEnvironment.asStateFlow()

    fun setServerEnvironment(value: String) {
        val isBeta = value == "beta"
        prefs.edit()
            .putString(KEY_SERVER_ENVIRONMENT, value)
            .putBoolean(KEY_USE_BETA_MODE, isBeta)
            .putBoolean(KEY_USE_BETA_SHARE_LINKS, isBeta)
            .apply()
        _serverEnvironment.value = value
    }

    /** Custom server URL for local dev testing (e.g. "http://192.168.1.100:3000"). */
    val customServerUrl: StateFlow<String> = _customServerUrl.asStateFlow()

    fun setCustomServerUrl(value: String) {
        prefs.edit().putString(KEY_CUSTOM_SERVER_URL, value).apply()
        _customServerUrl.value = value
    }

    /** Email for dev token endpoint on custom server. */
    val customDevEmail: StateFlow<String> = _customDevEmail.asStateFlow()

    fun setCustomDevEmail(value: String) {
        prefs.edit().putString(KEY_CUSTOM_DEV_EMAIL, value).apply()
        _customDevEmail.value = value
    }

    /** Backward-compatible computed property for auth key namespacing. */
    val useBetaMode: Boolean
        get() = _serverEnvironment.value == "beta"

    val useBetaModeFlow: StateFlow<Boolean>
        get() = MutableStateFlow(useBetaMode)

    /** The base URL for generating share links. */
    val shareBaseUrl: String
        get() = when (_serverEnvironment.value) {
            "beta" -> "https://share.beta.thedeadly.app"
            "custom" -> _customServerUrl.value
            else -> "https://share.thedeadly.app"
        }

    /** The base URL for API calls. */
    val apiBaseUrl: String
        get() = when (_serverEnvironment.value) {
            "beta" -> "https://beta.thedeadly.app"
            "custom" -> _customServerUrl.value
            else -> "https://thedeadly.app"
        }

    // ── Backward-compatible aliases ──────────────────────────────────

    /** @deprecated Use [serverEnvironment] instead. */
    val useBetaShareLinks: StateFlow<Boolean> get() = MutableStateFlow(useBetaMode)

    /** @deprecated Use [setServerEnvironment] instead. */
    fun setUseBetaShareLinks(value: Boolean) = setServerEnvironment(if (value) "beta" else "prod")

}
