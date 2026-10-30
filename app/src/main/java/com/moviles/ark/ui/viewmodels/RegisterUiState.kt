package com.moviles.ark.ui.viewmodels

//estado de la pantalla de registro con campos de entrada y errores reactivos inline
//errorMessage va en rojo debajo de los campos: resume que corregir o avisa que no hay internet
data class RegisterUiState(
    val name: String = "",
    val birthDate: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nameError: String? = null,
    val birthDateError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
