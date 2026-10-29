package com.moviles.ark.domain.repositories

import kotlinx.coroutines.flow.Flow

//avatar elegido por el usuario; el dominio no sabe que se guarda en SharedPreferences
interface AvatarRepository {
    //id del avatar actual; vuelve a emitir cada vez que cambia
    val currentAvatarId: Flow<String>

    fun setAvatarId(avatarId: String)
}
