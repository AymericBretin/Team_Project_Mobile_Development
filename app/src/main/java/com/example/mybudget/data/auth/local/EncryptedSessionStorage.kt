package com.example.mybudget.data.auth.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.example.mybudget.data.auth.AuthSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * DataStore-backed [SessionStorage] whose file is fully encrypted with [KeystoreCipher].
 *
 * DataStore keeps the decrypted value in memory after the first read, so subsequent reads
 * (e.g. on every HTTP request to attach the token) don't hit the Keystore.
 *
 * The file is excluded from backups (see res/xml/backup_rules.xml): the Keystore key doesn't follow
 * a restore onto another device, so the file would be unreadable there.
 */
class EncryptedSessionStorage(
    context: Context,
    cipher: KeystoreCipher = KeystoreCipher(),
    json: Json = Json { ignoreUnknownKeys = true },
) : SessionStorage {

    private val dataStore: DataStore<StoredSession> = DataStoreFactory.create(
        serializer = EncryptedSessionSerializer(cipher, json),
        produceFile = { context.applicationContext.dataStoreFile(FILE_NAME) },
    )

    override val session: Flow<AuthSession?> = dataStore.data.map { it.session }

    override suspend fun read(): AuthSession? = dataStore.data.first().session

    override suspend fun save(session: AuthSession) {
        dataStore.updateData { StoredSession(session) }
    }

    override suspend fun clear() {
        dataStore.updateData { StoredSession(null) }
    }

    companion object {
        const val FILE_NAME = "auth_session.pb"
    }
}

@Serializable
internal data class StoredSession(val session: AuthSession? = null)

private class EncryptedSessionSerializer(
    private val cipher: KeystoreCipher,
    private val json: Json,
) : Serializer<StoredSession> {

    override val defaultValue = StoredSession()

    override suspend fun readFrom(input: InputStream): StoredSession {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            json.decodeFromString(StoredSession.serializer(), cipher.decrypt(bytes).decodeToString())
        } catch (e: Exception) {
            // Keystore key lost/invalidated or corrupted file: fall back to an empty session
            // (the user signs in again) instead of crashing the app.
            Log.w(TAG, "Unreadable session, resetting", e)
            defaultValue
        }
    }

    override suspend fun writeTo(t: StoredSession, output: OutputStream) {
        val plaintext = json.encodeToString(StoredSession.serializer(), t).encodeToByteArray()
        output.write(cipher.encrypt(plaintext))
    }

    private companion object {
        const val TAG = "SessionStorage"
    }
}
