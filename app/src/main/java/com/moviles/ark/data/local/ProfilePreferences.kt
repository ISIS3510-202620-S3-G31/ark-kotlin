package com.moviles.ark.data.local

import android.content.Context
import android.content.SharedPreferences
import com.moviles.ark.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class AvatarPreset(
    val id: String,
    val drawableRes: Int,
    val name: String
)

data class ScalarUserProfile(
    val name: String = "",
    val email: String = "",
    val age: Int = 0,
    val avatarId: String = ProfilePreferences.DEFAULT_AVATAR_ID
)

//administrador de sharedpreferences para datos escalares del perfil de usuario y avatar
//permite persistir y cargar al instante en el arranque de la app sin bloquear el hilo principal
object ProfilePreferences {
    private const val PREFS_NAME = "ark_profile_prefs"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_USER_AGE = "user_age"
    const val KEY_AVATAR_ID = "avatar_id"
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

    private val _userProfile = MutableStateFlow(ScalarUserProfile())
    val userProfile: StateFlow<ScalarUserProfile> = _userProfile.asStateFlow()

    private var prefs: SharedPreferences? = null

    //inicializa las preferencias en el arranque de la aplicacion
    fun init(context: Context) {
        if (prefs == null) {
            val appCtx = context.applicationContext
            prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedAvatar = prefs?.getString(KEY_AVATAR_ID, DEFAULT_AVATAR_ID) ?: DEFAULT_AVATAR_ID
            val savedName = prefs?.getString(KEY_USER_NAME, "").orEmpty()
            val savedEmail = prefs?.getString(KEY_USER_EMAIL, "").orEmpty()
            val savedAge = prefs?.getInt(KEY_USER_AGE, 0) ?: 0

            _currentAvatarId.value = savedAvatar
            _userProfile.value = ScalarUserProfile(
                name = savedName,
                email = savedEmail,
                age = savedAge,
                avatarId = savedAvatar
            )
        }
    }

    //lectura directa de memoria o cache local
    fun getScalarProfile(): ScalarUserProfile = _userProfile.value

    fun getAvatarId(context: Context? = null): String {
        if (context != null) init(context)
        return _currentAvatarId.value
    }

    //persiste datos escalares del perfil de usuario en segundo plano usando dispatchers.io
    suspend fun saveProfile(
        name: String,
        email: String,
        age: Int,
        avatarId: String? = null
    ) = withContext(Dispatchers.IO) {
        val targetAvatar = avatarId ?: _currentAvatarId.value
        prefs?.edit()?.apply {
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_EMAIL, email)
            putInt(KEY_USER_AGE, age)
            putString(KEY_AVATAR_ID, targetAvatar)
            apply()
        }
        _currentAvatarId.value = targetAvatar
        _userProfile.value = ScalarUserProfile(
            name = name,
            email = email,
            age = age,
            avatarId = targetAvatar
        )
    }

    //guarda solo el avatar seleccionado en segundo plano con dispatchers.io
    suspend fun saveAvatarId(avatarId: String) = withContext(Dispatchers.IO) {
        prefs?.edit()?.putString(KEY_AVATAR_ID, avatarId)?.apply()
        _currentAvatarId.value = avatarId
        _userProfile.value = _userProfile.value.copy(avatarId = avatarId)
    }

    //modificacion sincronica de respaldo para llamadas directas
    fun setAvatarId(avatarId: String) {
        prefs?.edit()?.putString(KEY_AVATAR_ID, avatarId)?.apply()
        _currentAvatarId.value = avatarId
        _userProfile.value = _userProfile.value.copy(avatarId = avatarId)
    }

    fun setAvatarId(context: Context, avatarId: String) {
        init(context)
        setAvatarId(avatarId)
    }

    //limpia los datos escalares en caso de cerrar sesion
    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs?.edit()?.clear()?.apply()
        _currentAvatarId.value = DEFAULT_AVATAR_ID
        _userProfile.value = ScalarUserProfile()
    }

    fun getDrawableRes(avatarId: String?): Int {
        return PRESET_AVATARS.firstOrNull { it.id == avatarId }?.drawableRes ?: R.drawable.avatar_1
    }
}
