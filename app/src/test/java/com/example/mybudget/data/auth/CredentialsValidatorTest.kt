package com.example.mybudget.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialsValidatorTest {
    @Test
    fun `accepts valid credentials`() {
        assertNull(CredentialsValidator.validate("jane@example.com", "12345678"))
    }

    @Test
    fun `rejects malformed emails`() {
        listOf("", "jane", "jane@", "@example.com", "jane@example", "ja ne@example.com").forEach {
            assertEquals(it, AuthError.InvalidEmail, CredentialsValidator.validate(it, "12345678"))
        }
    }

    @Test
    fun `rejects short passwords`() {
        assertEquals(AuthError.PasswordTooShort, CredentialsValidator.validate("jane@example.com", "1234567"))
    }
}
