package com.moviles.ark.data.local

import android.content.Context
import android.util.SparseArray
import com.moviles.ark.domain.models.FortuneModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

//gestor de cache en memoria y disco para la galleta de la fortuna
class FortuneCacheManager(context: Context) {
    //cache en memoria con sparsearray para consultar si una galleta es favorita en 0 ms sin autoboxing
    private val favoritesCache = SparseArray<Boolean>()

    //archivos en cacheDir para las galletas recientes y la reserva sin conexion
    private val recentFile = File(context.cacheDir, "recent_cookies.json")
    private val deckFile = File(context.cacheDir, "fortune_deck.json")
    private val recentCapacity = 5

    //lee una lista de galletas desde un archivo json en el almacenamiento
    private fun readCookiesFromFile(file: File): List<FortuneModel> {
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            val array = JSONArray(content)
            val result = mutableListOf<FortuneModel>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getInt("id")
                result.add(
                    FortuneModel(
                        id = id,
                        quote = obj.getString("quote"),
                        category = obj.optString("category", "Wisdom"),
                        luckyNumber = obj.optInt("luckyNumber", 7),
                        isFavorite = isFavorite(id),
                        savedAt = obj.optLong("savedAt", System.currentTimeMillis())
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    //escribe una lista de galletas en formato json a un archivo en disco
    private fun writeCookiesToFile(file: File, cookies: List<FortuneModel>) {
        try {
            val array = JSONArray()
            for (item in cookies) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("quote", item.quote)
                obj.put("category", item.category)
                obj.put("luckyNumber", item.luckyNumber)
                obj.put("savedAt", item.savedAt)
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (_: Exception) {
            //si no se puede escribir en disco se omite silenciosamente
        }
    }

    //carga en memoria los ids de favoritos desde room al iniciar
    fun loadFavoriteIds(ids: List<Int>) {
        favoritesCache.clear()
        for (id in ids) {
            favoritesCache.put(id, true)
        }
    }

    //revisa si una galleta es favorita al instante en memoria
    fun isFavorite(cookieId: Int): Boolean {
        return favoritesCache.get(cookieId, false)
    }

    //actualiza el estado de favorito en memoria
    fun setFavorite(cookieId: Int, favorite: Boolean) {
        if (favorite) {
            favoritesCache.put(cookieId, true)
        } else {
            favoritesCache.delete(cookieId)
        }
    }

    //lee las galletas recientes guardadas en disco
    @Synchronized
    fun getRecentCookies(): List<FortuneModel> = readCookiesFromFile(recentFile)

    //agrega una galleta al carrusel de recientes usando politica lru de maximo 5 elementos
    @Synchronized
    fun addRecentCookie(cookie: FortuneModel): List<FortuneModel> {
        val current = getRecentCookies().toMutableList()
        current.removeAll { it.id == cookie.id }
        current.add(0, cookie)
        val trimmed = if (current.size > recentCapacity) current.take(recentCapacity) else current
        writeCookiesToFile(recentFile, trimmed)
        return trimmed
    }

    //frases de reserva offline por defecto por si el usuario abre la app sin internet
    private val defaultReserveQuotes = listOf(
        "Peace begins with a deep breath and a quiet mind.",
        "Small daily steps lead to life-changing results.",
        "Trust the timing of your life and keep moving forward.",
        "Your kindness to yourself is your greatest superpower.",
        "Happiness is not by chance, but by conscious daily choice."
    )

    //obtiene una frase de respaldo seguro del conjunto por defecto
    fun getDefaultFallback(): FortuneModel {
        val quote = defaultReserveQuotes.random()
        val id = FortuneModel.generateId(quote)
        return FortuneModel(id = id, quote = quote, category = "Wisdom", isFavorite = isFavorite(id))
    }

    //lee las galletas candidatas guardadas en disco como reserva offline
    @Synchronized
    fun getDeck(): List<FortuneModel> {
        if (!deckFile.exists()) {
            val defaults = defaultReserveQuotes.map { quote ->
                val id = FortuneModel.generateId(quote)
                FortuneModel(id = id, quote = quote, category = "Wisdom", isFavorite = isFavorite(id))
            }
            saveDeck(defaults)
            return defaults
        }
        return readCookiesFromFile(deckFile)
    }

    //retorna cuantas galletas quedan en el deck de reserva de disco
    @Synchronized
    fun getDeckCount(): Int = getDeck().size

    //agrega galletas nuevas traidas de la red a la reserva en disco sin duplicar frases
    @Synchronized
    fun appendDeck(newCookies: List<FortuneModel>) {
        val current = getDeck().toMutableList()
        val existingQuotes = current.map { it.quote }.toSet()
        for (cookie in newCookies) {
            if (!existingQuotes.contains(cookie.quote)) {
                current.add(cookie)
            }
        }
        saveDeck(current)
    }

    //guarda la baraja de galletas de reserva en disco
    @Synchronized
    fun saveDeck(cookies: List<FortuneModel>) {
        writeCookiesToFile(deckFile, cookies)
    }

    //saca una galleta de la reserva offline para mostrarla al usuario
    @Synchronized
    fun popDeckCookie(): FortuneModel? {
        val deck = getDeck().toMutableList()
        if (deck.isEmpty()) return null
        val cookie = deck.removeAt(0)
        saveDeck(deck)
        return cookie
    }
}
