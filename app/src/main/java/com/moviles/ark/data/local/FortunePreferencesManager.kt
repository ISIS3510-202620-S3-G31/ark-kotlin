package com.moviles.ark.data.local

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//galletas que quedan hoy, guardadas en sharedpreferences por usuario
class FortunePreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fortune_prefs", Context.MODE_PRIVATE)

    //fecha de hoy como 2026-11-24 para saber si cambio el dia, tambien es el nombre del documento en la nube
    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

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
        //queda pendiente de subir para que el otro telefono sepa cuantas se abrieron
        prefs.edit().putString("last_date_$userId", today()).putInt("chances_left_$userId", next).putBoolean("daily_pending_$userId", true).commit()
        return next
    }

    //galletas que ya se abrieron hoy
    fun getUsedToday(userId: String): Int = DAILY_CHANCES - getChancesLeft(userId)

    //guarda las abiertas hoy que llegaron de la nube, nunca devuelve galletas ya abiertas
    fun setUsedToday(userId: String, used: Int) {
        val safe = used.coerceIn(0, DAILY_CHANCES)
        if (safe <= getUsedToday(userId)) return
        prefs.edit().putString("last_date_$userId", today()).putInt("chances_left_$userId", DAILY_CHANCES - safe).commit()
    }

    //imagenes que ya se dibujaron con el estilo actual, las viejas se vuelven a dibujar al compartir
    fun isCardCurrent(userId: String, cookieId: Int): Boolean = prefs.getStringSet("cards_style_${CARD_STYLE}_$userId", emptySet())?.contains(cookieId.toString()) == true

    fun markCardCurrent(userId: String, cookieId: Int) {
        val current = prefs.getStringSet("cards_style_${CARD_STYLE}_$userId", emptySet()).orEmpty().toMutableSet()
        current.add(cookieId.toString())
        prefs.edit().putStringSet("cards_style_${CARD_STYLE}_$userId", current).apply()
    }

    //si hay galletas abiertas sin red que todavia no se subieron
    fun isDailyPending(userId: String): Boolean = prefs.getBoolean("daily_pending_$userId", false)

    fun setDailyPending(userId: String, pending: Boolean) {
        prefs.edit().putBoolean("daily_pending_$userId", pending).apply()
    }

    //imagenes que se quitaron y faltan por borrar en la nube, asi no vuelven al sincronizar
    fun getDeletedCards(userId: String): Set<Int> {
        val saved = prefs.getStringSet("deleted_cards_$userId", emptySet()) ?: emptySet()
        return saved.mapNotNull { it.toIntOrNull() }.toSet()
    }

    //anota una imagen quitada para borrarla en la nube cuando haya red
    fun addDeletedCard(userId: String, cookieId: Int) {
        val next = getDeletedCards(userId).map { it.toString() }.toMutableSet()
        next.add(cookieId.toString())
        prefs.edit().putStringSet("deleted_cards_$userId", next).commit()
    }

    //ya se borro en la nube, se quita de la lista
    fun removeDeletedCard(userId: String, cookieId: Int) {
        val next = getDeletedCards(userId).map { it.toString() }.toMutableSet()
        next.remove(cookieId.toString())
        prefs.edit().putStringSet("deleted_cards_$userId", next).commit()
    }

    companion object {
        const val DAILY_CHANCES = 5
        //sube cuando cambia el diseno de la imagen que se comparte
        const val CARD_STYLE = 2
    }
}
