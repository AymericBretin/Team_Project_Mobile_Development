package com.example.mybudget.data.auth

import kotlinx.serialization.Serializable

/** Signed-in user, as returned by the server. */
@Serializable
data class User(
    val id: String,
    val email: String,
    val displayName: String? = null,
)

/** Session persisted locally (encrypted) after a successful login / register / refresh. */
@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val user: User,
)

/** App-wide authentication state, observed by the UI to decide which screen to show. */
sealed interface AuthState {
    /** Local session is still being read (app startup). */
    data object Loading : AuthState

    data object Unauthenticated : AuthState

    data class Authenticated(val user: User) : AuthState
}

/** Outcome of an auth operation: either a value or a typed [AuthError]. */
sealed interface AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>

    data class Failure(val error: AuthError) : AuthResult<Nothing>
}

/** Auth errors the UI can map to user-facing messages. */
sealed interface AuthError {
    // Client-side validation (before any network call)
    data object InvalidEmail : AuthError
    data object PasswordTooShort : AuthError

    // Returned by the server
    data object InvalidCredentials : AuthError
    data object EmailAlreadyInUse : AuthError
    data class Validation(val message: String?) : AuthError
    data object TooManyRequests : AuthError
    data object Server : AuthError

    // Technical errors
    data object Network : AuthError
    data class Unknown(val message: String?) : AuthError
}
