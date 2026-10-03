package com.moviles.ark.ui.viewmodels

data class ProfileUiState(
    val name: String = "",
    //iniciales para el avatar, por ejemplo "DF" para "David Forero"
    val initials: String = "",
    val email: String = "",
    //fecha ya formateada para mostrar, por ejemplo "October 2026"
    val memberSince: String = "",
    //dias desde que creo la cuenta, contando el dia del registro como el 1; 0 si no se conoce
    val daysWithArk: Int = 0,
    //dias seguidos con check-in (#96); 0 si no hay racha o no se pudo leer
    val streakDays: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    //true despues de cerrar sesion: la pantalla lo ve y navega al login
    val isSignedOut: Boolean = false
)
