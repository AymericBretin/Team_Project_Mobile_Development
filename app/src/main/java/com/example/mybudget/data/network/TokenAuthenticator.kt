package com.example.mybudget.data.network

import com.example.mybudget.data.auth.TokenProvider
import com.example.mybudget.data.network.AuthInterceptor.Companion.AUTHORIZATION
import com.example.mybudget.data.network.AuthInterceptor.Companion.BEARER
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Called by OkHttp when the server answers 401: renews the access token and replays the request.
 *
 * Concurrent 401s trigger a single refresh (mutex in [TokenProvider]).
 * If the refresh fails, the request fails with the original 401 and the session is cleared,
 * which switches AuthState to Unauthenticated (the UI then goes back to the login screen).
 */
class TokenAuthenticator(private val tokenProvider: TokenProvider) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Request was not authenticated in the first place: nothing to refresh.
        val failedHeader = response.request.header(AUTHORIZATION) ?: return null
        // Already replayed after a refresh and still 401: give up to avoid looping.
        if (response.priorResponseCount() >= MAX_RETRIES) return null

        val failedToken = failedHeader.removePrefix("$BEARER ").trim()
        val newToken = runBlocking { tokenProvider.refreshAccessToken(failedToken) } ?: return null

        return response.request.newBuilder()
            .header(AUTHORIZATION, "$BEARER $newToken")
            .build()
    }

    private fun Response.priorResponseCount(): Int {
        var count = 0
        var prior = priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_RETRIES = 1
    }
}
