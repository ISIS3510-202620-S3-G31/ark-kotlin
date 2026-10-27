package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.exceptions.InvalidCredentialsException
import com.moviles.ark.domain.exceptions.NetworkUnavailableException
import com.moviles.ark.domain.models.LoginCredentials
import com.moviles.ark.domain.repositories.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    //las reglas de cada campo estan en el modelo LoginCredentials; aqui solo se guarda el resultado
    fun onEmailChange(newEmail: String) {
        val updated = _uiState.value.copy(email = newEmail, emailError = LoginCredentials.emailErrorWhileTyping(newEmail), errorMessage = null)
        _uiState.value = updated.copy(isFormValid = LoginCredentials(updated.email, updated.password).isValid())
    }

    fun onPasswordChange(newPassword: String) {
        val updated = _uiState.value.copy(password = newPassword, passwordError = LoginCredentials.passwordErrorWhileTyping(newPassword), errorMessage = null)
        _uiState.value = updated.copy(isFormValid = LoginCredentials(updated.email, updated.password).isValid())
    }

    fun dismissNoInternetDialog() {
        _uiState.value = _uiState.value.copy(showNoInternetDialog = false)
    }

    fun onLoginClick(onSuccess: () -> Unit) {
        val state = _uiState.value
        val credentials = LoginCredentials(state.email, state.password)
        val submitError = credentials.submitError()
        if (submitError != null) {
            _uiState.value = state.copy(emailError = credentials.emailSubmitError() ?: state.emailError, errorMessage = submitError)
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null, showNoInternetDialog = false)
        viewModelScope.launch {
            val result = authRepository.login(state.email, state.password)
            _uiState.value = _uiState.value.copy(isLoading = false)

            result.onSuccess { onSuccess() }
            result.onFailure { error ->
                //el repositorio ya traduce los errores de firebase a excepciones del dominio
                if (error is NetworkUnavailableException) {
                    _uiState.value = _uiState.value.copy(
                        showNoInternetDialog = true,
                        isAirplaneModeError = error.isAirplaneMode,
                        errorMessage = null
                    )
                } else {
                    val message = when (error) {
                        is InvalidCredentialsException -> "Incorrect email or password"
                        else -> "Could not log in, try again"
                    }
                    _uiState.value = _uiState.value.copy(errorMessage = message)
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                LoginViewModel(app.container.authRepository)
            }
        }
    }
}
