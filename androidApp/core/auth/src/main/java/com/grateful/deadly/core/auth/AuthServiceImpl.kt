package com.grateful.deadly.core.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.api.auth.AuthUser
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
) : AuthService {

    companion object {
        private const val TAG = "AuthService"
        private const val PREFS_NAME = "auth_secure_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_JSON = "auth_user_json"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient()
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

    private fun getTokenKey(): String =
        if (appPreferences.useBetaMode) "${KEY_TOKEN}_beta" else "${KEY_TOKEN}_prod"

    private fun getUserKey(): String =
        if (appPreferences.useBetaMode) "${KEY_USER_JSON}_beta" else "${KEY_USER_JSON}_prod"

    override suspend fun signInWithGoogle(activity: Activity) {
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
            throw IllegalStateException("Unexpected credential type: ${credential.type}")
        }
    }

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

        val response = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/auth/mobile/token")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("Token exchange failed (${response.code}): $errorBody")
        }

        val responseBody = response.body?.string() ?: throw RuntimeException("Empty response")
        val tokenResponse = json.decodeFromString<TokenResponse>(responseBody)

        // Save to prefs
        prefs.edit()
            .putString(getTokenKey(), tokenResponse.token)
            .putString(getUserKey(), json.encodeToString(SerializableAuthUser.serializer(), tokenResponse.user))
            .apply()

        _authState.value = AuthState.SignedIn(tokenResponse.user.toAuthUser())
    }

    override suspend fun signOut() {
        prefs.edit()
            .remove(getTokenKey())
            .remove(getUserKey())
            .apply()
        _authState.value = AuthState.SignedOut
    }

    override fun getAuthToken(): String? =
        prefs.getString(getTokenKey(), null)

    /** Call when beta mode toggle changes. */
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
