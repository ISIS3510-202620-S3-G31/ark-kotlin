package com.moviles.ark.ui.viewmodels

//estado de la pantalla de registro con campos de entrada y errores reactivos inline
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
    val isFormValid: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showNoInternetDialog: Boolean = false,
    val isAirplaneModeError: Boolean = false
)
