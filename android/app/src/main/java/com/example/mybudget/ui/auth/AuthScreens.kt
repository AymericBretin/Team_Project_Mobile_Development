package com.example.mybudget.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ButtonDefaults
import com.example.mybudget.data.auth.AuthError
import com.example.mybudget.ui.theme.DeepGreen
import com.example.mybudget.ui.theme.Mint

/** Messages suggested in docs/auth.md. */
fun AuthError.message(): String = when (this) {
    AuthError.InvalidEmail -> "Invalid email address"
    AuthError.PasswordTooShort -> "At least 8 characters"
    AuthError.InvalidCredentials -> "Wrong email or password"
    AuthError.EmailAlreadyInUse -> "An account already exists with this email"
    is AuthError.Validation -> message ?: "Please check your details"
    AuthError.TooManyRequests -> "Too many attempts, try again later"
    AuthError.Network -> "No internet connection"
    AuthError.Server, is AuthError.Unknown -> "Something went wrong, please try again"
}

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(DeepGreen), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("My Budget", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = Mint, modifier = Modifier.size(28.dp))
        }
    }
}

/** Login + register in one screen with a toggle, on the deck's dark green background. */
@Composable
fun AuthScreen(
    isLoading: Boolean,
    error: AuthError?,
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (email: String, password: String, displayName: String?) -> Unit,
    onFieldChange: () -> Unit,
) {
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val submit = {
        if (isRegister) onRegister(email.trim(), password, name.trim().ifBlank { null })
        else onLogin(email.trim(), password)
    }

    Box(Modifier.fillMaxSize().background(DeepGreen)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("MY BUDGET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            Text(
                if (isRegister) "Create your account" else "Welcome back",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Text("Know where your money goes", style = MaterialTheme.typography.bodyLarge, color = Mint)
            Spacer(Modifier.height(12.dp))

            val fieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Mint,
                unfocusedBorderColor = Mint.copy(alpha = 0.5f),
                focusedLabelColor = Mint,
                unfocusedLabelColor = Mint.copy(alpha = 0.8f),
                cursorColor = Mint,
                errorBorderColor = MaterialTheme.colorScheme.error,
                errorLabelColor = MaterialTheme.colorScheme.error,
                errorTextColor = Color.White,
            )

            if (isRegister) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; onFieldChange() },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; onFieldChange() },
                label = { Text("Email") },
                singleLine = true,
                isError = error == AuthError.InvalidEmail || error == AuthError.EmailAlreadyInUse,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; onFieldChange() },
                label = { Text("Password") },
                singleLine = true,
                isError = error == AuthError.PasswordTooShort || error == AuthError.InvalidCredentials,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = Mint,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!isLoading) submit() }),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Text(error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = { submit() },
                enabled = !isLoading && email.isNotBlank() && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color.White.copy(alpha = 0.12f),
                    disabledContentColor = Mint.copy(alpha = 0.7f),
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else Text(if (isRegister) "Create account" else "Log in")
            }

            TextButton(
                onClick = { isRegister = !isRegister; onFieldChange() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    if (isRegister) "Already have an account? Log in" else "No account yet? Sign up",
                    color = Mint,
                )
            }
        }
    }
}
