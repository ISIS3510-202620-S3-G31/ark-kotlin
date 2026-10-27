package com.moviles.ark.data.local

import android.content.Context
import android.content.SharedPreferences
import com.moviles.ark.domain.models.Avatar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

//guarda en el telefono el avatar elegido; solo lo usa AvatarRepositoryImpl
object AvatarPreferences {
    private const val PREFS_NAME = "ark_avatar_prefs"
    const val KEY_AVATAR_ID = "avatarId"
    const val DEFAULT_AVATAR_ID = Avatar.DEFAULT_ID

    private val _currentAvatarId = MutableStateFlow(DEFAULT_AVATAR_ID)
    val currentAvatarId: StateFlow<String> = _currentAvatarId.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            val appCtx = context.applicationContext
            prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs?.getString(KEY_AVATAR_ID, DEFAULT_AVATAR_ID) ?: DEFAULT_AVATAR_ID
            _currentAvatarId.value = saved
        }
    }

    fun getAvatarId(context: Context? = null): String {
        if (context != null) init(context)
        return _currentAvatarId.value
    }

    fun setAvatarId(avatarId: String) {
        prefs?.edit()?.putString(KEY_AVATAR_ID, avatarId)?.apply()
        _currentAvatarId.value = avatarId
    }

    fun setAvatarId(context: Context, avatarId: String) {
        init(context)
        prefs?.edit()?.putString(KEY_AVATAR_ID, avatarId)?.apply()
        _currentAvatarId.value = avatarId
    }
}
