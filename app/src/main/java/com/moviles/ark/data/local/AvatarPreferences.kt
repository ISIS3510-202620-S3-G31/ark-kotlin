package com.moviles.ark.data.local

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

//delegado de compatibilidad para que el codigo existente siga funcionando sin cambios
object AvatarPreferences {
    val PRESET_AVATARS: List<AvatarPreset> get() = ProfilePreferences.PRESET_AVATARS
    const val KEY_AVATAR_ID: String = ProfilePreferences.KEY_AVATAR_ID
    const val DEFAULT_AVATAR_ID: String = ProfilePreferences.DEFAULT_AVATAR_ID

    val currentAvatarId: StateFlow<String> get() = ProfilePreferences.currentAvatarId

    fun init(context: Context) = ProfilePreferences.init(context)
    fun getAvatarId(context: Context? = null): String = ProfilePreferences.getAvatarId(context)
    fun setAvatarId(avatarId: String) = ProfilePreferences.setAvatarId(avatarId)
    fun setAvatarId(context: Context, avatarId: String) = ProfilePreferences.setAvatarId(context, avatarId)
    fun getDrawableRes(avatarId: String?): Int = ProfilePreferences.getDrawableRes(avatarId)
}
