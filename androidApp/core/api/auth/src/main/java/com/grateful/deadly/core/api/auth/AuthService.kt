package com.grateful.deadly.core.api.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

interface AuthService {
    val authState: StateFlow<AuthState>
    suspend fun signInWithGoogle(activity: Activity)
    suspend fun signOut()
    fun getAuthToken(): String?
}
