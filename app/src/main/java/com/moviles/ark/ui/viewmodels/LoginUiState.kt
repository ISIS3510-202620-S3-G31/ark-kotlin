package com.moviles.ark.ui.viewmodels

//estado de la pantalla de inicio de sesion con errores inline reactivos
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isFormValid: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
