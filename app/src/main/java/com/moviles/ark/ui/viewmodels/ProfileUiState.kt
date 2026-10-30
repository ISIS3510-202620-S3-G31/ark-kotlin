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
    //foto subida por el usuario (archivo en el telefono); null si nunca subio una
    //se ofrece en el selector aunque ahora este mostrando un avatar
    val avatarPath: String? = null,
    //avatar de la lista que eligio
    val selectedAvatarId: String = Avatar.DEFAULT_ID,
    //true si eligio mostrar su foto en vez del avatar
    val showsPhoto: Boolean = false,
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
    val message: String? = null,
    //cosas guardadas solo en el telefono al tocar "Sign out"; mas de 0 abre el aviso antes de cerrar sesion
    val pendingBeforeSignOut: Int = 0
)
