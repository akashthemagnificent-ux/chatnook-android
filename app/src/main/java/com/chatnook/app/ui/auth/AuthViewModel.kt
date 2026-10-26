package com.chatnook.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatnook.app.data.Supabase
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AuthMode {
    object Login : AuthMode()
    object Signup : AuthMode()
}

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val notice: String? = null,
    val error: String? = null,
    val isLoading: Boolean = false,
    val mode: AuthMode = AuthMode.Login,
)

class AuthViewModel : ViewModel() {

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    /** Falls back to a constant flow when the build has no credentials. */
    val sessionStatus: StateFlow<SessionStatus> =
        Supabase.clientOrNull()?.auth?.sessionStatus
            ?: MutableStateFlow<SessionStatus>(SessionStatus.NotAuthenticated()).asStateFlow()

    fun selectMode(mode: AuthMode) {
        _ui.update { it.copy(mode = mode, error = null, notice = null) }
    }

    fun onEmailChange(email: String) {
        _ui.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        _ui.update { it.copy(password = password, error = null) }
    }

    fun onConfirmPasswordChange(password: String) {
        _ui.update { it.copy(confirmPassword = password, error = null) }
    }

    private suspend fun callAuth(notice: String? = null, block: suspend (Auth) -> Unit) {
        _ui.update { it.copy(isLoading = true, error = null, notice = null) }
        val client = Supabase.clientOrNull()
        if (client == null) {
            _ui.update { it.copy(isLoading = false, error = "Supabase credentials not configured") }
            return
        }
        try {
            block(client.auth)
            _ui.update { it.copy(isLoading = false, notice = notice) }
        } catch (e: Exception) {
            _ui.update { it.copy(isLoading = false, error = e.message ?: e.toString()) }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            callAuth("Check your email to confirm your account.") { auth ->
                auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            callAuth { auth ->
                auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { callAuth { auth -> auth.signOut() } }
    }

    fun onEmailPasswordTap() {
        val state = _ui.value
        when (state.mode) {
            AuthMode.Login -> signIn(state.email, state.password)
            AuthMode.Signup ->
                // Cheap client-side guard; Supabase enforces the real rule.
                if (state.password != state.confirmPassword) {
                    _ui.update { it.copy(error = "Passwords do not match") }
                } else {
                    signUp(state.email, state.password)
                }
        }
    }

    fun signInAnonymously() {
        viewModelScope.launch { callAuth { auth -> auth.signInAnonymously() } }
    }

    fun sendMagicLink(email: String) {
        viewModelScope.launch {
            callAuth("Magic link sent to $email.") { auth ->
                auth.signInWith(OTP) { this.email = email }
            }
        }
    }
}