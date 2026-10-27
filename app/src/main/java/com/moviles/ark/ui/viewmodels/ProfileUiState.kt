package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.Avatar

data class ProfileUiState(
    val name: String = "",
    //iniciales para el avatar, por ejemplo "DF" para "David Forero"
    val initials: String = "",
    val email: String = "",
    //calculada desde la fecha de nacimiento; 0 si no se conoce
    val age: Int = 0,
    //fecha de nacimiento YYYY-MM-DD; vacia en cuentas viejas que solo guardaron la edad
    val birthDate: String = "",
    //ruta del avatar en el telefono; null muestra las iniciales
    val avatarPath: String? = null,
    //avatar preset seleccionado localmente
    val selectedAvatarId: String = Avatar.DEFAULT_ID,
    //fecha ya formateada para mostrar, por ejemplo "October 2026"
    val memberSince: String = "",
    //dias desde que creo la cuenta, contando el dia del registro como el 1; 0 si no se conoce
    val daysWithArk: Int = 0,
    //dias seguidos con check-in; 0 si no hay racha o no se pudo leer
    val streakDays: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    //true despues de cerrar sesion: la pantalla lo ve y navega al login
    val isSignedOut: Boolean = false,
    //dialogo para editar nombre y fecha de nacimiento; los campos son texto mientras el usuario escribe
    val isEditing: Boolean = false,
    val editName: String = "",
    val editBirthDate: String = "",
    val editError: String? = null,
    val isSaving: Boolean = false,
    //mensaje corto despues de guardar o si la foto no se pudo usar
    val message: String? = null
)
