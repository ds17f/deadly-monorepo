package com.grateful.deadly.core.api.auth

sealed class AuthState {
    data object SignedOut : AuthState()
    data class SignedIn(val user: AuthUser) : AuthState()
}
