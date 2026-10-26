package com.moviles.ark.ui.viewmodels

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showNoInternetDialog: Boolean = false,
    val isAirplaneModeError: Boolean = false
)
