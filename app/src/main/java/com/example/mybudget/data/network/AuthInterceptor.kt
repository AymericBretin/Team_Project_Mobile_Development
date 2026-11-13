package com.example.mybudget.data.network

import com.example.mybudget.data.auth.TokenProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Adds `Authorization: Bearer <token>` to authenticated API requests. */
class AuthInterceptor(private val tokenProvider: TokenProvider) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) != null) return chain.proceed(request)

        // OkHttp runs interceptors on its own threads, so runBlocking is safe here,
        // and the read is served from DataStore's in-memory cache.
        val token = runBlocking { tokenProvider.accessToken() } ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header(AUTHORIZATION, "$BEARER $token").build(),
        )
    }

    companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer"
    }
}
