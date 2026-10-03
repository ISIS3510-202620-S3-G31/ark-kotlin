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
    //  Privado: solo el ViewModel puede modificarlo
    private val _uiState = MutableStateFlow(LoginUiState()) // se le pasa los valores por defecto que estan en ese archivo

    //  Público: la pantalla solo lo escucha
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(newEmail: String) {
        _uiState.value = _uiState.value.copy(email = newEmail, errorMessage = null) // hacemos la copia por lo que los valores son inmutables y aja entonces hacemos copia modificando el nuevo valor y el otro se elimina
    }

    fun onPasswordChange(newPassword: String) {
        _uiState.value = _uiState.value.copy(password = newPassword, errorMessage = null)
    }

    fun onLoginClick(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please fill in all fields")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.login(state.email, state.password)
            _uiState.value = _uiState.value.copy(isLoading = false)

            result.onSuccess { onSuccess() }
            result.onFailure { error ->
                // firebase no dice si fallo el correo o la clave, por eso es un solo mensaje
                val message = when (error) {
                    is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> "Incorrect email or password"
                    is FirebaseNetworkException -> "No internet connection, try again"
                    else -> "Could not log in, try again"
                }
                _uiState.value = _uiState.value.copy(errorMessage = message)
            }
        }
    }

    // factory para que el viewmodel reciba el authRepository del AppContainer
    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                LoginViewModel(app.container.authRepository)
            }
        }
    }
}
