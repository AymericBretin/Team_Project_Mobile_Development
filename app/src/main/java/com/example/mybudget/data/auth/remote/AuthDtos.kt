package com.example.mybudget.data.auth.remote

import com.example.mybudget.data.auth.AuthSession
import com.example.mybudget.data.auth.User
import kotlinx.serialization.Serializable

// Auth API DTOs. The full contract is documented in docs/auth.md.

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String? = null)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String)

@Serializable
data class UserDto(val id: String, val email: String, val displayName: String? = null)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long? = null,
    val user: UserDto,
)

@Serializable
data class ErrorResponse(val code: String? = null, val message: String? = null)

fun UserDto.toDomain() = User(id = id, email = email, displayName = displayName)

fun AuthResponse.toSession() = AuthSession(
    accessToken = accessToken,
    refreshToken = refreshToken,
    user = user.toDomain(),
)
