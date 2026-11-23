package com.example.mybudget.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mybudget.MyBudgetApplication
import com.example.mybudget.data.auth.AuthError
import com.example.mybudget.data.auth.AuthRepository
import com.example.mybudget.data.auth.AuthResult
import com.example.mybudget.data.auth.AuthState
import com.example.mybudget.data.auth.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the login / register forms. */
data class AuthUiState(
    val isLoading: Boolean = false,
    val error: AuthError? = null,
)

/**
 * Auth ViewModel exposed to the UI.
 *
 * Compose usage:
 * ```
 * val vm: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
 * val authState by vm.authState.collectAsStateWithLifecycle()
 * val uiState by vm.uiState.collectAsStateWithLifecycle()
 * ```
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    /** Drive navigation from this: Loading → splash, Unauthenticated → login, Authenticated → app. */
    val authState: StateFlow<AuthState> = repository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) = submit { repository.login(email, password) }

    fun register(email: String, password: String, displayName: String? = null) =
        submit { repository.register(email, password, displayName) }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    /** Call when the user edits a field, to hide the displayed error. */
    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun submit(action: suspend () -> AuthResult<User>) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val error = (action() as? AuthResult.Failure)?.error
            // On success authState switches to Authenticated, navigation follows from it.
            _uiState.update { it.copy(isLoading = false, error = error) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MyBudgetApplication
                AuthViewModel(app.container.authRepository)
            }
        }
    }
}
