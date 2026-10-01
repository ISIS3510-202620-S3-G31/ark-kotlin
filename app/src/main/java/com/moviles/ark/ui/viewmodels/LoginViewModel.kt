package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LoginViewModel : ViewModel() {
    //  Privado: solo el ViewModel puede modificarlo
    private val _uiState = MutableStateFlow(LoginUiState()) // se le pasa los valores por defecto que estan en ese archivo

    //  Público: la pantalla solo lo escucha
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(newEmail: String) {
        _uiState.value = _uiState.value.copy(email = newEmail) // hacemos la copia por lo que los valores son inmutables y aja entonces hacemos copia modificando el nuevo valor y el otro se elimina
    }

    fun onPasswordChange(newPassword: String) {
        _uiState.value = _uiState.value.copy(password = newPassword)
    }

    fun onLoginClick() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        //TODO Aqui se debe llamar a el servicio de auth para que funcione correctamente
    }
}
