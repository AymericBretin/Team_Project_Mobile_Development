package com.example.mybudget.data.network

import com.example.mybudget.data.auth.TokenProvider
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenAuthenticatorTest {

    private class FakeTokenProvider(private val newToken: String?) : TokenProvider {
        var refreshedWith: String? = null
        override suspend fun accessToken(): String? = null
        override suspend fun refreshAccessToken(failedAccessToken: String?): String? {
            refreshedWith = failedAccessToken
            return newToken
        }
    }

    private fun request(token: String?) = Request.Builder()
        .url("https://api.example.com/budgets")
        .apply { if (token != null) header("Authorization", "Bearer $token") }
        .build()

    private fun unauthorized(request: Request, prior: Response? = null) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(401)
        .message("Unauthorized")
        .priorResponse(prior)
        .build()

    @Test
    fun `retries with refreshed token`() {
        val provider = FakeTokenProvider("new-token")

        val retried = TokenAuthenticator(provider).authenticate(null, unauthorized(request("old-token")))

        assertEquals("old-token", provider.refreshedWith)
        assertEquals("Bearer new-token", retried?.header("Authorization"))
    }

    @Test
    fun `gives up when refresh fails`() {
        assertNull(TokenAuthenticator(FakeTokenProvider(null)).authenticate(null, unauthorized(request("old-token"))))
    }

    @Test
    fun `ignores requests that were not authenticated`() {
        val provider = FakeTokenProvider("new-token")
        assertNull(TokenAuthenticator(provider).authenticate(null, unauthorized(request(null))))
        assertNull(provider.refreshedWith)
    }

    @Test
    fun `does not loop when retried request is still unauthorized`() {
        val first = unauthorized(request("old-token"))
        val second = unauthorized(request("new-token"), prior = first)
        assertNull(TokenAuthenticator(FakeTokenProvider("newer-token")).authenticate(null, second))
    }
}
