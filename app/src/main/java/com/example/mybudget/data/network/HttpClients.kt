package com.example.mybudget.data.network

import com.example.mybudget.BuildConfig
import com.example.mybudget.data.auth.TokenProvider
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object HttpClients {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Base client, no auth. Used as-is for the `auth/...` endpoints. */
    fun base(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        // BASIC: no bodies, so passwords and tokens are never logged.
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader(AuthInterceptor.AUTHORIZATION)
                    },
                )
            }
        }
        .build()

    /** Client for every other API: attaches the token and renews it automatically on 401. */
    fun authenticated(base: OkHttpClient, tokenProvider: TokenProvider): OkHttpClient = base.newBuilder()
        .addInterceptor(AuthInterceptor(tokenProvider))
        .authenticator(TokenAuthenticator(tokenProvider))
        .build()

    fun retrofit(client: OkHttpClient, baseUrl: String = BuildConfig.API_BASE_URL): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
