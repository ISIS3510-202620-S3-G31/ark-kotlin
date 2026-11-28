package com.moviles.ark.data.local

import android.content.Context

//marca por cuenta de si este telefono ya bajo alguna vez sus galletas de la nube (shared preferences)
//sirve para distinguir un telefono nuevo sin red (las galletas estan en la nube) de un jarro que de verdad esta vacio
class CookieSyncPreferences(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun hasDownloaded(userId: String): Boolean = preferences.getBoolean(keyOf(userId), false)

    fun markDownloaded(userId: String) {
        preferences.edit().putBoolean(keyOf(userId), true).apply()
    }

    private fun keyOf(userId: String) = "downloaded_$userId"

    companion object {
        private const val FILE_NAME = "cookie_sync"
    }
}
