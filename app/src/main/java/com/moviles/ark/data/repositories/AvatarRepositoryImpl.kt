package com.moviles.ark.data.repositories

import com.moviles.ark.data.local.AvatarPreferences
import com.moviles.ark.domain.repositories.AvatarRepository
import kotlinx.coroutines.flow.Flow

//guarda el avatar en SharedPreferences a traves de AvatarPreferences
class AvatarRepositoryImpl : AvatarRepository {
    override val currentAvatarId: Flow<String> = AvatarPreferences.currentAvatarId

    override fun setAvatarId(avatarId: String) {
        AvatarPreferences.setAvatarId(avatarId)
    }
}
