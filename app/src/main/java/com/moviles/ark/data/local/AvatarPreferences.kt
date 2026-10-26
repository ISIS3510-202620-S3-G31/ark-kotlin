package com.moviles.ark.data.local

import android.content.Context
import android.content.SharedPreferences
import com.moviles.ark.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AvatarPreset(
    val id: String,
    val drawableRes: Int,
    val name: String
)

object AvatarPreferences {
    private const val PREFS_NAME = "ark_avatar_prefs"
    const val KEY_AVATAR_ID = "avatarId"
    const val DEFAULT_AVATAR_ID = "avatar_1"

    val PRESET_AVATARS: List<AvatarPreset> = listOf(
        AvatarPreset("avatar_1", R.drawable.avatar_1, "Fox"),
        AvatarPreset("avatar_2", R.drawable.avatar_2, "Owl"),
        AvatarPreset("avatar_3", R.drawable.avatar_3, "Spark"),
        AvatarPreset("avatar_4", R.drawable.avatar_4, "Panda"),
        AvatarPreset("avatar_5", R.drawable.avatar_5, "Cat"),
        AvatarPreset("avatar_6", R.drawable.avatar_6, "Sprout")
    )

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

    fun getDrawableRes(avatarId: String?): Int {
        return PRESET_AVATARS.firstOrNull { it.id == avatarId }?.drawableRes ?: R.drawable.avatar_1
    }
}
