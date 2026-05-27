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
        private const val KEY_INSTALL_DATE = "install_date"
        private const val KEY_UNIQUE_SHOWS_PLAYED = "unique_shows_played"
        private const val KEY_LAST_REVIEW_PROMPT_TIME = "last_review_prompt_time"
        private const val KEY_HAS_ADDED_FAVORITE = "has_added_favorite"
        private const val KEY_DEVELOPER_MODE_UNLOCKED = "developer_mode_unlocked"
        private const val KEY_PLAYER_CONTROLS_STYLE = "player_controls_style"
        private const val KEY_HOME_TRENDING_WINDOW = "home_trending_window"
        private const val KEY_HOME_TRENDING_ABOVE_TODAY = "home_trending_above_today"
        private const val KEY_HOME_RECENT_ROWS = "home_recent_rows"
        private const val KEY_HOME_TRENDING_CARD_SIZE = "home_trending_card_size"
        private const val KEY_HOME_TODAY_CARD_SIZE = "home_today_card_size"
        private const val KEY_HOME_COLLECTIONS_CARD_SIZE = "home_collections_card_size"
        private const val KEY_HOME_TRENDING_INCLUDE_ANNIVERSARIES = "home_trending_include_anniversaries"
        private const val KEY_HOME_POPULAR_ENABLED = "home_popular_enabled"
        private const val KEY_HOME_POPULAR_CARD_SIZE = "home_popular_card_size"
    }

    private val _homeTrendingCardSize = MutableStateFlow(
        prefs.getString(KEY_HOME_TRENDING_CARD_SIZE, null) ?: "small"
    )
    val homeTrendingCardSize: StateFlow<String> = _homeTrendingCardSize.asStateFlow()
    fun setHomeTrendingCardSize(value: String) {
        prefs.edit().putString(KEY_HOME_TRENDING_CARD_SIZE, value).apply()
        _homeTrendingCardSize.value = value
    }

    private val _homeTodayCardSize = MutableStateFlow(
        prefs.getString(KEY_HOME_TODAY_CARD_SIZE, null) ?: "large"
    )
    val homeTodayCardSize: StateFlow<String> = _homeTodayCardSize.asStateFlow()
    fun setHomeTodayCardSize(value: String) {
        prefs.edit().putString(KEY_HOME_TODAY_CARD_SIZE, value).apply()
        _homeTodayCardSize.value = value
    }

    private val _homeCollectionsCardSize = MutableStateFlow(
        prefs.getString(KEY_HOME_COLLECTIONS_CARD_SIZE, null) ?: "large"
    )
    val homeCollectionsCardSize: StateFlow<String> = _homeCollectionsCardSize.asStateFlow()
    fun setHomeCollectionsCardSize(value: String) {
        prefs.edit().putString(KEY_HOME_COLLECTIONS_CARD_SIZE, value).apply()
        _homeCollectionsCardSize.value = value
    }

    /** Restore all Home Screen preferences to defaults. */
    fun resetHomePreferences() {
        setHomeTrendingWindow("now")
        setHomeTrendingAboveToday(false)
        setHomeRecentRows(2)
        setHomeTrendingCardSize("small")
        setHomeTodayCardSize("large")
        setHomeCollectionsCardSize("large")
        setHomeTrendingIncludeAnniversaries(false)
        setHomePopularEnabled(true)
        setHomePopularCardSize("small")
    }

    private val _homePopularEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_HOME_POPULAR_ENABLED, true)
    )

    /** Show the "Fan Favorites" home rail. On by default. */
    val homePopularEnabled: StateFlow<Boolean> = _homePopularEnabled.asStateFlow()

    fun setHomePopularEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_HOME_POPULAR_ENABLED, value).apply()
        _homePopularEnabled.value = value
    }

    private val _homePopularCardSize = MutableStateFlow(
        prefs.getString(KEY_HOME_POPULAR_CARD_SIZE, null) ?: "small"
    )
    val homePopularCardSize: StateFlow<String> = _homePopularCardSize.asStateFlow()
    fun setHomePopularCardSize(value: String) {
        prefs.edit().putString(KEY_HOME_POPULAR_CARD_SIZE, value).apply()
        _homePopularCardSize.value = value
    }

    private val _homeTrendingIncludeAnniversaries = MutableStateFlow(
        prefs.getBoolean(KEY_HOME_TRENDING_INCLUDE_ANNIVERSARIES, false)
    )

    /**
     * When true, "Today in Grateful Dead History" shows are included in the
     * `now` trending window. Off by default so the 24h ranking surfaces
     * organic momentum instead of echoing the OTD home rail.
     */
    val homeTrendingIncludeAnniversaries: StateFlow<Boolean> =
        _homeTrendingIncludeAnniversaries.asStateFlow()

    fun setHomeTrendingIncludeAnniversaries(value: Boolean) {
        prefs.edit().putBoolean(KEY_HOME_TRENDING_INCLUDE_ANNIVERSARIES, value).apply()
        _homeTrendingIncludeAnniversaries.value = value
    }

    private val _homeTrendingAboveToday = MutableStateFlow(
        prefs.getBoolean(KEY_HOME_TRENDING_ABOVE_TODAY, false)
    )

    /** When true, the Trending section renders above Today in History; otherwise below. */
    val homeTrendingAboveToday: StateFlow<Boolean> = _homeTrendingAboveToday.asStateFlow()

    fun setHomeTrendingAboveToday(value: Boolean) {
        prefs.edit().putBoolean(KEY_HOME_TRENDING_ABOVE_TODAY, value).apply()
        _homeTrendingAboveToday.value = value
    }

    private val _homeRecentRows = MutableStateFlow(
        prefs.getInt(KEY_HOME_RECENT_ROWS, 2).coerceIn(1, 4)
    )

    /** How many rows of Recently Played to render on the home screen (1..4). Each row holds 2 shows. */
    val homeRecentRows: StateFlow<Int> = _homeRecentRows.asStateFlow()

    fun setHomeRecentRows(value: Int) {
        val clamped = value.coerceIn(1, 4)
        prefs.edit().putInt(KEY_HOME_RECENT_ROWS, clamped).apply()
        _homeRecentRows.value = clamped
    }

    private val _homeTrendingWindow = MutableStateFlow(
        prefs.getString(KEY_HOME_TRENDING_WINDOW, null) ?: "now"
    )

    /** Which trending window the home screen shows. One of "now"/"week"/"month"/"all". */
    val homeTrendingWindow: StateFlow<String> = _homeTrendingWindow.asStateFlow()

    fun setHomeTrendingWindow(value: String) {
        prefs.edit().putString(KEY_HOME_TRENDING_WINDOW, value).apply()
        _homeTrendingWindow.value = value
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

    // ── Player Controls ──────────────────────────────────────────────────

    private val _playerControlsStyle = MutableStateFlow(
        prefs.getString(KEY_PLAYER_CONTROLS_STYLE, null) ?: "SKIP_TRACK"
    )

    /** Notification / lock screen / AA transport buttons: SKIP_TRACK, SKIP_SECONDS, or BOTH. */
    val playerControlsStyle: StateFlow<String> = _playerControlsStyle.asStateFlow()

    fun setPlayerControlsStyle(value: String) {
        prefs.edit().putString(KEY_PLAYER_CONTROLS_STYLE, value).apply()
        _playerControlsStyle.value = value
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

    // ── In-App Review ────────────────────────────────────────────────

    val installDate: Long = run {
        val existing = prefs.getLong(KEY_INSTALL_DATE, 0L)
        if (existing != 0L) {
            existing
        } else {
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_INSTALL_DATE, now).apply()
            now
        }
    }

    private val uniqueShowsPlayedSet: MutableSet<String> =
        (prefs.getStringSet(KEY_UNIQUE_SHOWS_PLAYED, emptySet()) ?: emptySet()).toMutableSet()

    val uniqueShowsPlayedCount: Int
        @Synchronized get() = uniqueShowsPlayedSet.size

    @Synchronized
    fun recordShowPlayed(showId: String) {
        if (uniqueShowsPlayedSet.add(showId)) {
            prefs.edit().putStringSet(KEY_UNIQUE_SHOWS_PLAYED, uniqueShowsPlayedSet.toSet()).apply()
        }
    }

    fun getLastReviewPromptTime(): Long = prefs.getLong(KEY_LAST_REVIEW_PROMPT_TIME, 0L)

    fun setLastReviewPromptTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_REVIEW_PROMPT_TIME, time).apply()
    }

    fun getHasAddedFavorite(): Boolean = prefs.getBoolean(KEY_HAS_ADDED_FAVORITE, false)

    fun setHasAddedFavorite(value: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_ADDED_FAVORITE, value).apply()
    }

    private val _developerModeUnlocked = MutableStateFlow(
        prefs.getBoolean(KEY_DEVELOPER_MODE_UNLOCKED, false)
    )

    val developerModeUnlocked: StateFlow<Boolean> = _developerModeUnlocked.asStateFlow()

    fun setDeveloperModeUnlocked(value: Boolean) {
        prefs.edit().putBoolean(KEY_DEVELOPER_MODE_UNLOCKED, value).apply()
        _developerModeUnlocked.value = value
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
