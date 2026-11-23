package com.example.mybudget.data.auth

import com.example.mybudget.data.auth.local.SessionStorage
import com.example.mybudget.data.auth.remote.AuthApi
import com.example.mybudget.data.auth.remote.ErrorResponse
import com.example.mybudget.data.auth.remote.LoginRequest
import com.example.mybudget.data.auth.remote.LogoutRequest
import com.example.mybudget.data.auth.remote.RefreshRequest
import com.example.mybudget.data.auth.remote.RegisterRequest
import com.example.mybudget.data.auth.remote.AuthResponse
import com.example.mybudget.data.auth.remote.toSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

class DefaultAuthRepository(
    private val api: AuthApi,
    private val storage: SessionStorage,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AuthRepository, TokenProvider {

    private val refreshMutex = Mutex()

    override val authState: Flow<AuthState> = storage.session
        .map { session -> if (session == null) AuthState.Unauthenticated else AuthState.Authenticated(session.user) }
        .distinctUntilChanged()

    override suspend fun login(email: String, password: String): AuthResult<User> {
        CredentialsValidator.validate(email, password)?.let { return AuthResult.Failure(it) }
        return authenticate { api.login(LoginRequest(email.trim(), password)) }
    }

    override suspend fun register(email: String, password: String, displayName: String?): AuthResult<User> {
        CredentialsValidator.validate(email, password)?.let { return AuthResult.Failure(it) }
        val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
        return authenticate { api.register(RegisterRequest(email.trim(), password, name)) }
    }

    override suspend fun logout() {
        val session = storage.read() ?: return
        try {
            api.logout("Bearer ${session.accessToken}", LogoutRequest(session.refreshToken))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort: server-side revocation is nice to have but must never block logout.
        } finally {
            storage.clear()
        }
    }

    override suspend fun accessToken(): String? = storage.read()?.accessToken

    override suspend fun refreshAccessToken(failedAccessToken: String?): String? = refreshMutex.withLock {
        val current = storage.read() ?: return null
        if (failedAccessToken != null && current.accessToken != failedAccessToken) {
            return current.accessToken
        }
        try {
            val newSession = api.refresh(RefreshRequest(current.refreshToken)).toSession()
            storage.save(newSession)
            newSession.accessToken
        } catch (e: HttpException) {
            // Refresh token expired or revoked: the session is dead, log the user out.
            if (e.code() in 400..499) storage.clear()
            null
        } catch (_: IOException) {
            // Network issue: keep the session, a later request will retry.
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Unexpected response (e.g. malformed JSON): don't crash the original HTTP call.
            null
        }
    }

    private suspend fun authenticate(call: suspend () -> AuthResponse): AuthResult<User> = try {
        val session = call().toSession()
        storage.save(session)
        AuthResult.Success(session.user)
    } catch (e: HttpException) {
        AuthResult.Failure(e.toAuthError())
    } catch (_: IOException) {
        AuthResult.Failure(AuthError.Network)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Failure(AuthError.Unknown(e.message))
    }

    private fun HttpException.toAuthError(): AuthError {
        val body = parseError()
        return when (code()) {
            401 -> AuthError.InvalidCredentials
            409 -> AuthError.EmailAlreadyInUse
            400, 422 -> AuthError.Validation(body?.message)
            429 -> AuthError.TooManyRequests
            in 500..599 -> AuthError.Server
            else -> AuthError.Unknown(body?.message ?: message())
        }
    }

    private fun HttpException.parseError(): ErrorResponse? = try {
        response()?.errorBody()?.string()?.let { json.decodeFromString(ErrorResponse.serializer(), it) }
    } catch (_: Exception) {
        null
    }
}
