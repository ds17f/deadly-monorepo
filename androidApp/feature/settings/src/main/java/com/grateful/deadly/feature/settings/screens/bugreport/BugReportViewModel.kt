package com.grateful.deadly.feature.settings.screens.bugreport

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.database.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Named

/** UI state for the one-tap "Send Bug Report" upload. */
sealed interface BugReportSendState {
    data object Idle : BugReportSendState
    data object Sending : BugReportSendState
    data object Success : BugReportSendState
    data class Failure(val message: String) : BugReportSendState
}

/**
 * Uploads the captured logs + metadata to the server. Gated by the same
 * X-Analytics-Key the app already embeds; stamps the signed-in user when a
 * token exists, otherwise the report is anonymous.
 */
@HiltViewModel
class BugReportViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val authService: AuthService,
    @Named("analyticsApiKey") private val apiKey: String,
    @Named("appVersionName") private val appVersion: String,
) : ViewModel() {

    private val _state = MutableStateFlow<BugReportSendState>(BugReportSendState.Idle)
    val state: StateFlow<BugReportSendState> = _state

    fun resetState() {
        _state.value = BugReportSendState.Idle
    }

    fun send(logs: String, note: String) {
        if (logs.isBlank() || _state.value is BugReportSendState.Sending) return
        _state.value = BugReportSendState.Sending
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { upload(logs, note) }
        }
    }

    private fun upload(logs: String, note: String): BugReportSendState {
        val baseUrl = appPreferences.apiBaseUrl
        val connection = URL("$baseUrl/api/bug-reports").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Analytics-Key", apiKey)
            authService.getAuthToken()?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
            }
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000

            val payload = JSONObject().apply {
                put("logs", logs)
                if (note.isNotBlank()) put("note", note.trim())
                put("platform", "android")
                put("appVersion", appVersion)
                put("osVersion", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("installId", appPreferences.installId)
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            when (val code = connection.responseCode) {
                in 200..299 -> BugReportSendState.Success
                429 -> BugReportSendState.Failure("Too many reports — please try again later.")
                else -> BugReportSendState.Failure("Send failed ($code). Try Copy or Share instead.")
            }
        } catch (e: Exception) {
            BugReportSendState.Failure("Couldn't reach the server. Try Copy or Share instead.")
        } finally {
            connection.disconnect()
        }
    }
}
