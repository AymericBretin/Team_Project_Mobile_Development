package com.example.mybudget.data.auth.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Auth endpoints. Backed by an HTTP client *without* the refresh authenticator to avoid loops
 * (a 401 on auth/refresh must not trigger another refresh).
 */
interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") authorization: String,
        @Body body: LogoutRequest,
    )
}
