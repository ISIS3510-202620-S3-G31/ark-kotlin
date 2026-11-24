package com.moviles.ark.data.local

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//gestor de preferencias con sharedpreferences para controlar los intentos diarios de romper galletas
class FortunePreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fortune_prefs", Context.MODE_PRIVATE)

    //obtiene la fecha de hoy en formato anio mes dia para comparar
    private fun getTodayDateString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    //devuelve las oportunidades que le quedan al usuario hoy, si es un nuevo dia las reinicia a 5
    fun getChancesLeft(): Int {
        val today = getTodayDateString()
        val lastDate = prefs.getString("fortune_last_date", null)
        if (lastDate != today) {
            //nuevo dia natural, reiniciamos los 5 intentos y guardamos la fecha de hoy
            prefs.edit().putString("fortune_last_date", today).putInt("fortune_chances_left", 5).apply()
            return 5
        }
        return prefs.getInt("fortune_chances_left", 5)
    }

    //descuenta una oportunidad cuando el usuario rompe una galleta
    fun decrementChances(): Int {
        val current = getChancesLeft()
        val next = (current - 1).coerceAtLeast(0)
        prefs.edit().putInt("fortune_chances_left", next).apply()
        return next
    }
}
