package com.example.mybudget.data.auth

/** Client-side credentials validation, to avoid a server round trip on obviously invalid input. */
object CredentialsValidator {
    const val MIN_PASSWORD_LENGTH = 8

    // Intentionally loose: strict validation is the server's job.
    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun validate(email: String, password: String): AuthError? = when {
        !EMAIL_REGEX.matches(email.trim()) -> AuthError.InvalidEmail
        password.length < MIN_PASSWORD_LENGTH -> AuthError.PasswordTooShort
        else -> null
    }
}
