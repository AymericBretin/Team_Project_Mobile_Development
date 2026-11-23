package com.example.mybudget.data.auth.local

import com.example.mybudget.data.auth.AuthSession
import kotlinx.coroutines.flow.Flow

/** Persistence for the auth session (tokens + user). */
interface SessionStorage {
    /** Emits the current session, or `null` when signed out. */
    val session: Flow<AuthSession?>

    suspend fun read(): AuthSession?

    suspend fun save(session: AuthSession)

    suspend fun clear()
}
