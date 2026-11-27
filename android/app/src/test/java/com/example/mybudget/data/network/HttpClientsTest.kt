package com.example.mybudget.data.network

import com.example.mybudget.data.auth.TokenProvider
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class HttpClientsTest {

    private val noTokens = object : TokenProvider {
        override suspend fun accessToken(): String? = null
        override suspend fun refreshAccessToken(failedAccessToken: String?): String? = null
    }

    @Test
    fun `token refresh never queues behind the requests waiting for it`() {
        val base = HttpClients.base()
        val authenticated = HttpClients.authenticated(base, noTokens)

        // Requests blocked in TokenAuthenticator hold their slot in the authenticated client's dispatcher;
        // the refresh call goes through the base client, so it must not share that dispatcher.
        assertNotSame(base.dispatcher, authenticated.dispatcher)
        assertSame(base.connectionPool, authenticated.connectionPool)
    }
}
