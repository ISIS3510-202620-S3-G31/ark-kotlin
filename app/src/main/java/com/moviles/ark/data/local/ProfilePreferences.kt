package com.moviles.ark.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ScalarUserProfile(
    val name: String = "",
    val email: String = "",
    val age: Int = 0
)

//administrador de sharedpreferences para datos escalares del perfil de usuario
//permite persistir y cargar al instante en el arranque de la app sin bloquear el hilo principal
//la foto de perfil (avatar o foto) ya no vive aqui: esta en room por cuenta y en firestore (UserRepositoryImpl)
object ProfilePreferences {
    private const val PREFS_NAME = "ark_profile_prefs"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_USER_AGE = "user_age"
    const val KEY_AVATAR_ID = "avatar_id"
    private const val KEY_SYNC_NOTICE = "sync_notice"

    private val _userProfile = MutableStateFlow(ScalarUserProfile())
    val userProfile: StateFlow<ScalarUserProfile> = _userProfile.asStateFlow()

    private var prefs: SharedPreferences? = null

    //inicializa las preferencias en el arranque de la aplicacion
    fun init(context: Context) {
        if (prefs == null) {
            val appCtx = context.applicationContext
            prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedName = prefs?.getString(KEY_USER_NAME, "").orEmpty()
            val savedEmail = prefs?.getString(KEY_USER_EMAIL, "").orEmpty()
            val savedAge = prefs?.getInt(KEY_USER_AGE, 0) ?: 0

            _userProfile.value = ScalarUserProfile(
                name = savedName,
                email = savedEmail,
                age = savedAge
            )
        }
    }

    //lectura directa de memoria o cache local
    fun getScalarProfile(): ScalarUserProfile = _userProfile.value

    //avatar que la cuenta eligio en versiones anteriores de la app, cuando se guardaba aqui ("avatar_id_<uid>");
    //el repositorio lo lee una sola vez para subirlo a firestore y no perderlo; null si no hay
    fun savedAvatarFor(uid: String): String? {
        return prefs?.getString("${KEY_AVATAR_ID}_$uid", null)
    }

    //aviso para la pantalla de perfil cuando el sync dejo la version de otro telefono; queda por cuenta
    //porque el sync corre en segundo plano y el usuario lo ve la proxima vez que abra su perfil
    fun saveSyncNotice(uid: String, notice: String) {
        prefs?.edit()?.putString("${KEY_SYNC_NOTICE}_$uid", notice)?.apply()
    }

    //entrega el aviso una sola vez
    fun takeSyncNotice(uid: String): String? {
        val key = "${KEY_SYNC_NOTICE}_$uid"
        val notice = prefs?.getString(key, null) ?: return null
        prefs?.edit()?.remove(key)?.apply()
        return notice
    }

    //persiste datos escalares del perfil de usuario en segundo plano usando dispatchers.io
    suspend fun saveProfile(
        name: String,
        email: String,
        age: Int
    ) = withContext(Dispatchers.IO) {
        prefs?.edit()?.apply {
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_EMAIL, email)
            putInt(KEY_USER_AGE, age)
            apply()
        }
        _userProfile.value = ScalarUserProfile(
            name = name,
            email = email,
            age = age
        )
    }

    //limpia los datos escalares en caso de cerrar sesion; los avatares viejos de cada cuenta ("avatar_id_<uid>")
    //se quedan hasta que el repositorio los sube a firestore
    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs?.edit()
            ?.remove(KEY_USER_NAME)
            ?.remove(KEY_USER_EMAIL)
            ?.remove(KEY_USER_AGE)
            ?.remove(KEY_AVATAR_ID)
            ?.apply()
        _userProfile.value = ScalarUserProfile()
    }
}
