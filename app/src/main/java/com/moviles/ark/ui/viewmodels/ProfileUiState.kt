package com.moviles.ark.ui.viewmodels

data class ProfileUiState(
    val name: String = "",
    //iniciales para el avatar, por ejemplo "DF" para "David Forero"
    val initials: String = "",
    val email: String = "",
    //0 si no se conoce
    val age: Int = 0,
    //ruta del avatar en el telefono; null muestra las iniciales (#119)
    val avatarPath: String? = null,
    //fecha ya formateada para mostrar, por ejemplo "October 2026"
    val memberSince: String = "",
    //dias desde que creo la cuenta, contando el dia del registro como el 1; 0 si no se conoce
    val daysWithArk: Int = 0,
    //dias seguidos con check-in (#96); 0 si no hay racha o no se pudo leer
    val streakDays: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    //true despues de cerrar sesion: la pantalla lo ve y navega al login
    val isSignedOut: Boolean = false,
    //dialogo para editar nombre y edad (#119); los campos son texto mientras el usuario escribe
    val isEditing: Boolean = false,
    val editName: String = "",
    val editAge: String = "",
    val editError: String? = null,
    val isSaving: Boolean = false,
    //mensaje corto despues de guardar o si la foto no se pudo usar
    val message: String? = null
)
