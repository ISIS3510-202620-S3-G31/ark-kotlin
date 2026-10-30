package com.moviles.ark.ui.viewmodels

//estado de la pantalla de inicio de sesion con errores inline reactivos
//errorMessage va en rojo debajo de los campos: clave incorrecta o sin internet
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
