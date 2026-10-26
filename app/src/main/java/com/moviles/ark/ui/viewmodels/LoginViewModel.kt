package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.repositories.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(newEmail: String) {
        val current = _uiState.value
        val emailError = when {
            newEmail.isNotBlank() && (newEmail.contains("@") && newEmail.contains(".")) && !newEmail.matches(EMAIL_REGEX) -> "Please enter a valid email address"
            newEmail.length >= 6 && !newEmail.contains("@") -> "Email must contain @"
            else -> null
        }
        val updated = current.copy(email = newEmail, emailError = emailError, errorMessage = null)
        _uiState.value = updated.copy(isFormValid = checkFormValidity(updated))
    }

    fun onPasswordChange(newPassword: String) {
        val current = _uiState.value
        val passError = when {
            newPassword.isNotBlank() && newPassword.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }
        val updated = current.copy(password = newPassword, passwordError = passError, errorMessage = null)
        _uiState.value = updated.copy(isFormValid = checkFormValidity(updated))
    }

    private fun checkFormValidity(state: LoginUiState): Boolean {
        val emailValid = state.email.isNotBlank() && state.email.matches(EMAIL_REGEX)
        val passValid = state.password.length >= 6
        return emailValid && passValid
    }

    fun onLoginClick(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please fill in all fields")
            return
        }

        if (!state.email.matches(EMAIL_REGEX)) {
            _uiState.value = state.copy(emailError = "Please enter a valid email address", errorMessage = "Please check highlighted errors")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.login(state.email, state.password)
            _uiState.value = _uiState.value.copy(isLoading = false)

            result.onSuccess { onSuccess() }
            result.onFailure { error ->
                val message = when (error) {
                    is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> "Incorrect email or password"
                    is FirebaseNetworkException -> "No internet connection, try again"
                    else -> "Could not log in, try again"
                }
                _uiState.value = _uiState.value.copy(errorMessage = message)
            }
        }
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                LoginViewModel(app.container.authRepository)
            }
        }
    }
}
