package com.grateful.deadly.core.database

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppReviewManager @Inject constructor(
    private val appPreferences: AppPreferences,
    private val analyticsService: AnalyticsService
) {
    companion object {
        private const val INSTALL_AGE_DAYS = 7
        private const val MIN_UNIQUE_SHOWS = 3
        private const val COOLDOWN_DAYS = 90
        private const val MILLIS_PER_DAY = 1000L * 60 * 60 * 24
    }

    private val _showPrePromptDialog = MutableStateFlow(false)
    val showPrePromptDialog: StateFlow<Boolean> = _showPrePromptDialog.asStateFlow()

    private val _launchInAppReview = MutableStateFlow(false)
    val launchInAppReview: StateFlow<Boolean> = _launchInAppReview.asStateFlow()

    fun checkAndMaybePrompt() {
        if (!areConditionsMet()) return
        _showPrePromptDialog.value = true
        analyticsService.track("review_prompt_shown")
    }

    fun onUserSaidYes() {
        _showPrePromptDialog.value = false
        _launchInAppReview.value = true
        appPreferences.setLastReviewPromptTime(System.currentTimeMillis())
        analyticsService.track("review_prompt_accepted")
    }

    fun onUserSaidNotReally() {
        _showPrePromptDialog.value = false
        appPreferences.setLastReviewPromptTime(System.currentTimeMillis())
        analyticsService.track("review_prompt_declined")
    }

    fun onInAppReviewLaunched() {
        _launchInAppReview.value = false
    }

    private fun areConditionsMet(): Boolean {
        val now = System.currentTimeMillis()

        val daysSinceInstall = (now - appPreferences.installDate) / MILLIS_PER_DAY
        if (daysSinceInstall < INSTALL_AGE_DAYS) return false

        if (!appPreferences.getHasAddedFavorite()) return false

        if (appPreferences.uniqueShowsPlayedCount < MIN_UNIQUE_SHOWS) return false

        val lastPrompt = appPreferences.getLastReviewPromptTime()
        if (lastPrompt > 0) {
            val daysSinceLastPrompt = (now - lastPrompt) / MILLIS_PER_DAY
            if (daysSinceLastPrompt < COOLDOWN_DAYS) return false
        }

        return true
    }
}
