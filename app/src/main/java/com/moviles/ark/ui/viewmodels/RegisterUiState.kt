package com.moviles.ark.ui.viewmodels

//Se deja vacio porque es lo que el usuario vera apenas
//entre a la pantalla de registro y que asi ponga sus datos

data class RegisterUiState(
    val name: String = "",
    val age: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null //? es diciendole que errorMessage es un string que puede estar nulo
)