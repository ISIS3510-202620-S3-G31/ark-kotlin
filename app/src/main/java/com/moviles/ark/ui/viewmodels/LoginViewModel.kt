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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
    //true cuando hay internet; vuelve a emitir cada vez que cambia
    connectivity: Flow<Boolean> = emptyFlow()
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    //el aviso de "sin internet" se quita solo cuando vuelve la red; antes se quedaba hasta que el usuario escribiera
    init {
        viewModelScope.launch {
            connectivity.collect { isOnline ->
                val noInternet = setOf(LoginCredentials.noInternetError(true), LoginCredentials.noInternetError(false))
                if (isOnline && _uiState.value.errorMessage in noInternet) {
                    _uiState.value = _uiState.value.copy(errorMessage = null)
                }
            }
        }
    }

    //las reglas de cada campo estan en el modelo LoginCredentials; aqui solo se guarda el resultado
    fun onEmailChange(newEmail: String) {
        _uiState.value = _uiState.value.copy(email = newEmail, emailError = LoginCredentials.emailErrorWhileTyping(newEmail), errorMessage = null)
    }

    fun onPasswordChange(newPassword: String) {
        _uiState.value = _uiState.value.copy(password = newPassword, passwordError = LoginCredentials.passwordErrorWhileTyping(newPassword), errorMessage = null)
    }

    //el toque nunca se ignora: si un campo esta vacio o mal escrito se dice debajo de ese campo
    fun onLoginClick(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isLoading) return
        val credentials = LoginCredentials(state.email, state.password)
        if (!credentials.isValid()) {
            _uiState.value = state.copy(emailError = credentials.emailError(), passwordError = credentials.passwordError(), errorMessage = null)
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.login(state.email, state.password)
            _uiState.value = _uiState.value.copy(isLoading = false)

            result.onSuccess { onSuccess() }
            result.onFailure { error ->
                //el repositorio ya traduce los errores de firebase a excepciones del dominio
                //sin internet se avisa en rojo debajo de los campos, sin detalles tecnicos
                val message = when (error) {
                    is NetworkUnavailableException -> LoginCredentials.noInternetError(error.isAirplaneMode)
                    is InvalidCredentialsException -> "Incorrect email or password"
                    else -> "Could not log in, try again"
                }
                _uiState.value = _uiState.value.copy(errorMessage = message)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                LoginViewModel(app.container.authRepository, app.container.networkConnectivityObserver.observe())
            }
        }
    }
}
