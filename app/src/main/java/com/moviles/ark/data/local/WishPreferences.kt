package com.moviles.ark.data.local

import android.content.Context
import android.content.SharedPreferences

//preferencias del farol de deseos guardadas en el telefono (sharedpreferences)
//guarda si los deseos de cada usuario ya se recuperaron de firestore
//si el usuario borra los datos de la app, esto tambien se borra, y por eso la app sabe que debe recuperarlos otra vez
class WishPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    //true si los deseos de este usuario ya se trajeron de firestore en este telefono
    fun areWishesRestored(userId: String): Boolean {
        return prefs.getBoolean(restoredKey(userId), false)
    }

    //se llama cuando la recuperacion termino bien
    fun markWishesRestored(userId: String) {
        prefs.edit()
            .putBoolean(restoredKey(userId), true)
            .apply()
    }

    //una llave por usuario: si en el mismo telefono entra otra cuenta, tambien se le recuperan sus deseos
    private fun restoredKey(userId: String): String {
        return "${KEY_WISHES_RESTORED}_$userId"
    }

    companion object {
        private const val PREFS_NAME = "ark_wish_prefs"
        private const val KEY_WISHES_RESTORED = "wishes_restored"
    }
}
