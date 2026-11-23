package com.example.mybudget.data.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class DefaultAuthRepositoryTest {

    private val api = FakeAuthApi()
    private val storage = InMemorySessionStorage()
    private val repository = DefaultAuthRepository(api, storage)

    @Test
    fun `login success persists session and emits Authenticated`() = runTest {
        val result = repository.login(" jane@example.com ", "password123")

        assertEquals(AuthResult.Success(testUser), result)
        assertEquals("access-1", storage.read()?.accessToken)
        assertEquals(AuthState.Authenticated(testUser), repository.authState.first())
    }

    @Test
    fun `login with invalid input fails without calling the server`() = runTest {
        assertEquals(AuthResult.Failure(AuthError.InvalidEmail), repository.login("not-an-email", "password123"))
        assertEquals(AuthResult.Failure(AuthError.PasswordTooShort), repository.login("jane@example.com", "short"))
        assertEquals(0, api.loginCalls)
    }

    @Test
    fun `login maps HTTP and network errors`() = runTest {
        api.loginResult = { throw httpError(401) }
        assertEquals(AuthResult.Failure(AuthError.InvalidCredentials), repository.login("jane@example.com", "password123"))

        api.loginResult = { throw httpError(422, """{"code":"VALIDATION_ERROR","message":"Email invalide"}""") }
        assertEquals(AuthResult.Failure(AuthError.Validation("Email invalide")), repository.login("jane@example.com", "password123"))

        api.loginResult = { throw httpError(503) }
        assertEquals(AuthResult.Failure(AuthError.Server), repository.login("jane@example.com", "password123"))

        api.loginResult = { throw IOException("offline") }
        assertEquals(AuthResult.Failure(AuthError.Network), repository.login("jane@example.com", "password123"))

        assertNull(storage.read())
    }

    @Test
    fun `register maps 409 to EmailAlreadyInUse and trims blank display name`() = runTest {
        api.registerResult = { throw httpError(409) }
        assertEquals(
            AuthResult.Failure(AuthError.EmailAlreadyInUse),
            repository.register("jane@example.com", "password123", "   "),
        )
        assertNull(api.lastRegister?.displayName)
    }

    @Test
    fun `logout clears session even when server call fails`() = runTest {
        storage.save(authSession())
        api.logoutResult = { throw IOException("offline") }

        repository.logout()

        assertEquals("Bearer access-1", api.lastLogoutAuthorization)
        assertNull(storage.read())
        assertEquals(AuthState.Unauthenticated, repository.authState.first())
    }

    @Test
    fun `refresh saves the new tokens`() = runTest {
        storage.save(authSession())

        assertEquals("access-2", repository.refreshAccessToken("access-1"))
        assertEquals("refresh-2", storage.read()?.refreshToken)
    }

    @Test
    fun `refresh is skipped when token was already renewed by another request`() = runTest {
        storage.save(authSession(access = "access-2"))

        assertEquals("access-2", repository.refreshAccessToken("access-1"))
        assertEquals(0, api.refreshCalls)
    }

    @Test
    fun `refresh rejected by server logs the user out`() = runTest {
        storage.save(authSession())
        api.refreshResult = { throw httpError(401) }

        assertNull(repository.refreshAccessToken("access-1"))
        assertNull(storage.read())
    }

    @Test
    fun `refresh network failure keeps the session`() = runTest {
        storage.save(authSession())
        api.refreshResult = { throw IOException("offline") }

        assertNull(repository.refreshAccessToken("access-1"))
        assertEquals("access-1", storage.read()?.accessToken)
    }

    private fun authSession(access: String = "access-1") =
        AuthSession(accessToken = access, refreshToken = "refresh-1", user = testUser)
}
