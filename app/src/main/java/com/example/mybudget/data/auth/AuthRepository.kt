package com.example.mybudget.data.auth

import kotlinx.coroutines.flow.Flow

/** Single entry point for auth for the rest of the app (ViewModels, etc.). */
interface AuthRepository {
    /** Current auth state, updated automatically (login, logout, expired session…). */
    val authState: Flow<AuthState>

    suspend fun login(email: String, password: String): AuthResult<User>

    suspend fun register(email: String, password: String, displayName: String? = null): AuthResult<User>

    /** Signs the user out. The local session is always cleared, even if the server is unreachable. */
    suspend fun logout()
}

/** Used by the network layer to get / renew the access token. */
interface TokenProvider {
    suspend fun accessToken(): String?

    /**
     * Renews the access token after a 401.
     *
     * @param failedAccessToken the token that was rejected. If the session already holds a different token
     *   (refreshed meanwhile by another request), that one is returned without a new network call.
     * @return the new access token, or `null` if the session is no longer valid (the user is then signed out).
     */
    suspend fun refreshAccessToken(failedAccessToken: String?): String?
}
