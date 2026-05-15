package com.grateful.deadly.core.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.api.auth.AuthUser
import com.grateful.deadly.core.database.AnalyticsService
import com.grateful.deadly.core.database.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AuthServiceImpl(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val googleClientId: String,
    private val analyticsService: AnalyticsService? = null,
    private val httpClient: OkHttpClient,
) : AuthService {

    companion object {
        private const val TAG = "AuthService"
        private const val PREFS_NAME = "auth_secure_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_JSON = "auth_user_json"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val credentialManager = CredentialManager.create(context)

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val token = prefs.getString(getTokenKey(), null)
        val userJson = prefs.getString(getUserKey(), null)
        if (token != null && userJson != null) {
            try {
                val user = json.decodeFromString<SerializableAuthUser>(userJson)
                _authState.value = AuthState.SignedIn(user.toAuthUser())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore session", e)
            }
        }
    }

    /** Fetch a Bearer token from the custom server's dev-token endpoint. */
    suspend fun fetchDevToken() {
        val email = appPreferences.customDevEmail.value
        val baseUrl = appPreferences.customServerUrl.value
        Log.d(TAG, "fetchDevToken: email='$email' baseUrl='$baseUrl'")
        if (email.isEmpty() || baseUrl.isEmpty()) {
            Log.w(TAG, "fetchDevToken: email or baseUrl is empty, aborting")
            return
        }

        try {
            val url = "$baseUrl/api/auth/dev-token?email=${java.net.URLEncoder.encode(email, "UTF-8")}"
            Log.d(TAG, "fetchDevToken: requesting $url")
            val response = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute()
            }
            Log.d(TAG, "fetchDevToken: response code=${response.code}")
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return
                val tokenResponse = json.decodeFromString<DevTokenResponse>(body)
                Log.d(TAG, "fetchDevToken: got token, fetching user info")
                // Fetch real user info using the token
                val meResponse = withContext(Dispatchers.IO) {
                    val meRequest = Request.Builder()
                        .url("${appPreferences.apiBaseUrl}/api/auth/me")
                        .addHeader("Authorization", "Bearer ${tokenResponse.token}")
                        .get().build()
                    httpClient.newCall(meRequest).execute()
                }
                if (meResponse.isSuccessful) {
                    val meBody = meResponse.body?.string() ?: return
                    val user = json.decodeFromString<SerializableAuthUser>(meBody)
                    prefs.edit()
                        .putString(getTokenKey(), tokenResponse.token)
                        .putString(getUserKey(), json.encodeToString(SerializableAuthUser.serializer(), user))
                        .apply()
                    _authState.value = AuthState.SignedIn(user.toAuthUser())
                    Log.d(TAG, "fetchDevToken: signed in as ${user.name ?: user.email}")
                } else {
                    Log.w(TAG, "fetchDevToken: /me failed (${meResponse.code})")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch dev token", e)
        }
    }

    @Serializable
    private data class DevTokenResponse(val token: String)

    private fun getTokenKey(): String =
        "${KEY_TOKEN}_${appPreferences.serverEnvironment.value}"

    private fun getUserKey(): String =
        "${KEY_USER_JSON}_${appPreferences.serverEnvironment.value}"

    override suspend fun signInWithGoogle(activity: Activity) {
        trackAuthEvent("sign_in_attempt", provider = "google")
        try {
            // Use GetSignInWithGoogleOption which always shows the Google Sign-In
            // bottom sheet, even when no credentials are cached on the device.
            // GetGoogleIdOption silently fails with NoCredentialException if the
            // OAuth client isn't fully propagated or no accounts are authorized.
            val signInOption = GetSignInWithGoogleOption.Builder(googleClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                exchangeToken(provider = "google", idToken = idToken)
            } else {
                trackAuthEvent("sign_in_failure", provider = "google", errorReason = "invalid_credential")
                throw IllegalStateException("Unexpected credential type: ${credential.type}")
            }
        } catch (e: TokenExchangeException) {
            trackAuthEvent("sign_in_failure", provider = "google", errorReason = e.reason)
            throw e
        } catch (e: Exception) {
            trackAuthEvent("sign_in_failure", provider = "google", errorReason = classifyAuthError(e))
            throw e
        }
    }

    /**
     * Map credential / network exceptions to a small fixed vocabulary so we
     * never write provider-side strings (which can include user emails on
     * "user not found" responses) into analytics.
     */
    private fun classifyAuthError(e: Throwable): String = when (e) {
        is GetCredentialCancellationException -> "cancelled"
        is NoCredentialException -> "no_credential"
        is IllegalStateException -> "invalid_credential"
        is java.io.IOException -> "network"
        else -> "unknown"
    }

    /** Thrown by exchangeToken to carry an already-classified reason out. */
    private class TokenExchangeException(val reason: String, message: String) : RuntimeException(message)

    private suspend fun exchangeToken(provider: String, idToken: String, name: String? = null) {
        val baseUrl = appPreferences.apiBaseUrl
        val bodyMap = buildMap {
            put("provider", provider)
            put("idToken", idToken)
            if (name != null) put("name", name)
        }
        val bodyJson = json.encodeToString(
            kotlinx.serialization.serializer<Map<String, String>>(),
            bodyMap,
        )

        val response = try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("$baseUrl/api/auth/mobile/token")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(request).execute()
            }
        } catch (e: java.io.IOException) {
            throw TokenExchangeException("network", "Token exchange network error")
        }

        if (!response.isSuccessful) {
            val code = response.code
            response.body?.close()
            throw TokenExchangeException("server_$code", "Token exchange failed ($code)")
        }

        val responseBody = response.body?.string()
            ?: throw TokenExchangeException("decode_error", "Empty response")
        val tokenResponse = try {
            json.decodeFromString<TokenResponse>(responseBody)
        } catch (e: Exception) {
            throw TokenExchangeException("decode_error", "Token exchange decode error")
        }

        // Save to prefs
        prefs.edit()
            .putString(getTokenKey(), tokenResponse.token)
            .putString(getUserKey(), json.encodeToString(SerializableAuthUser.serializer(), tokenResponse.user))
            .apply()

        _authState.value = AuthState.SignedIn(tokenResponse.user.toAuthUser())
        trackAuthEvent("sign_in_success", provider = provider)
    }

    private fun trackAuthEvent(feature: String, provider: String, errorReason: String? = null) {
        val props = buildMap<String, Any> {
            put("feature", feature)
            put("category", "account")
            put("provider", provider)
            if (errorReason != null) put("error_reason", errorReason)
        }
        analyticsService?.track("feature_use", props)
    }

    override suspend fun signOut() {
        prefs.edit()
            .remove(getTokenKey())
            .remove(getUserKey())
            .apply()
        _authState.value = AuthState.SignedOut
    }

    override fun getAuthToken(): String? {
        return prefs.getString(getTokenKey(), null)
    }

    /** Call when environment changes so the service picks up the right token. */
    fun onEnvironmentChanged() {
        _authState.value = AuthState.SignedOut
        restoreSession()
    }

    @Serializable
    private data class TokenResponse(
        val token: String,
        val user: SerializableAuthUser,
    )

    @Serializable
    private data class SerializableAuthUser(
        val id: String,
        val email: String? = null,
        val name: String? = null,
    ) {
        fun toAuthUser() = AuthUser(id = id, email = email, name = name)
    }
}
