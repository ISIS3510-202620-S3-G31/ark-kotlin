package com.moviles.ark.data.local

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//galletas que quedan hoy, guardadas en sharedpreferences por usuario
class FortunePreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fortune_prefs", Context.MODE_PRIVATE)

    //fecha de hoy como 2026-11-24 para saber si cambio el dia
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    //galletas que le quedan hoy al usuario, si es un dia nuevo vuelven a ser 5
    fun getChancesLeft(userId: String): Int {
        val lastDate = prefs.getString("last_date_$userId", null)
        if (lastDate != today()) return DAILY_CHANCES
        return prefs.getInt("chances_left_$userId", DAILY_CHANCES)
    }

    //resta una galleta y la guarda con la fecha de hoy
    //commit en vez de apply para que quede escrito de una vez aunque se cierre la app
    fun decrementChances(userId: String): Int {
        val next = (getChancesLeft(userId) - 1).coerceAtLeast(0)
        prefs.edit().putString("last_date_$userId", today()).putInt("chances_left_$userId", next).commit()
        return next
    }

    companion object {
        const val DAILY_CHANCES = 5
    }
}
