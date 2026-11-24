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
    fun getRecentCookies(): List<FortuneModel> {
        if (!recentFile.exists()) return emptyList()
        return try {
            val content = recentFile.readText()
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

    //agrega una galleta al carrusel de recientes usando politica lru de maximo 5 elementos
    @Synchronized
    fun addRecentCookie(cookie: FortuneModel): List<FortuneModel> {
        val current = getRecentCookies().toMutableList()
        //removemos si ya estaba para moverla a la primera posicion
        current.removeAll { it.id == cookie.id }
        current.add(0, cookie)
        //desalojamos las mas viejas si supera la capacidad de 5
        val trimmed = if (current.size > recentCapacity) current.take(recentCapacity) else current
        try {
            val array = JSONArray()
            for (item in trimmed) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("quote", item.quote)
                obj.put("category", item.category)
                obj.put("luckyNumber", item.luckyNumber)
                obj.put("savedAt", item.savedAt)
                array.put(obj)
            }
            recentFile.writeText(array.toString())
        } catch (_: Exception) {
            //si falla la escritura en disco no bloqueamos la interfaz
        }
        return trimmed
    }

    //lee las galletas candidatas guardadas en disco como reserva offline
    @Synchronized
    fun getDeck(): List<FortuneModel> {
        if (!deckFile.exists()) return emptyList()
        return try {
            val content = deckFile.readText()
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

    //guarda la baraja de galletas de reserva en disco
    @Synchronized
    fun saveDeck(cookies: List<FortuneModel>) {
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
            deckFile.writeText(array.toString())
        } catch (_: Exception) {
            //si no se puede escribir la reserva en disco se omite silenciosamente
        }
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
