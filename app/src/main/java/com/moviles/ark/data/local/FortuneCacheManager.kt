package com.moviles.ark.data.local

import android.content.Context
import android.util.SparseArray
import com.moviles.ark.domain.models.FortuneModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

//cache de la galleta de la fortuna en memoria y en disco
class FortuneCacheManager(private val context: Context) {
    //en memoria, id de la frase -> si es favorita, se consulta al instante sin ir a room
    private val favoritesCache = SparseArray<Boolean>()

    //en disco, las ultimas frases abiertas y las frases traidas del api que aun no se abren
    //cada usuario tiene su propio archivo de recientes, asi no se mezclan al cambiar de cuenta
    private fun recentFile(userId: String) = File(context.cacheDir, "recent_cookies_${userId.hashCode()}.json")
    private val deckFile = File(context.cacheDir, "fortune_deck.json")
    private val recentCapacity = 5

    //convierte texto json en una lista de frases
    private fun fromJson(text: String): List<FortuneModel> {
        val result = mutableListOf<FortuneModel>()
        try {
            val array = JSONArray(text)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val quote = obj.getString("quote")
                val id = obj.optInt("id", FortuneModel.generateId(quote))
                result.add(FortuneModel(id = id, quote = quote, category = obj.optString("category", "Wisdom"), isFavorite = isFavorite(id), savedAt = obj.optLong("savedAt", System.currentTimeMillis())))
            }
        } catch (_: Exception) {
            //si el json esta danado se devuelve lo que se alcanzo a leer
        }
        return result
    }

    //lee un archivo de disco, si no existe devuelve la lista vacia
    private fun readFile(file: File): List<FortuneModel> {
        if (!file.exists()) return emptyList()
        return try { fromJson(file.readText()) } catch (_: Exception) { emptyList() }
    }

    //escribe una lista de frases en un archivo de disco
    private fun writeFile(file: File, cookies: List<FortuneModel>) {
        val array = JSONArray()
        for (item in cookies) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("quote", item.quote)
            obj.put("category", item.category)
            obj.put("savedAt", item.savedAt)
            array.put(obj)
        }
        try { file.writeText(array.toString()) } catch (_: Exception) { }
    }

    //carga los ids de las favoritas al abrir la herramienta
    fun loadFavoriteIds(ids: List<Int>) {
        favoritesCache.clear()
        for (id in ids) favoritesCache.put(id, true)
    }

    //dice al instante si una frase es favorita
    fun isFavorite(cookieId: Int): Boolean = favoritesCache.get(cookieId, false)

    //cambia el estado de favorita en memoria
    fun setFavorite(cookieId: Int, favorite: Boolean) {
        if (favorite) favoritesCache.put(cookieId, true) else favoritesCache.delete(cookieId)
    }

    //ultimas frases abiertas por este usuario
    @Synchronized
    fun getRecentCookies(userId: String): List<FortuneModel> = readFile(recentFile(userId))

    //pone la frase de primera en recientes, si pasan de 5 se va la menos reciente
    @Synchronized
    fun addRecentCookie(userId: String, cookie: FortuneModel) {
        val list = readFile(recentFile(userId)).toMutableList()
        list.removeAll { it.id == cookie.id }
        list.add(0, cookie)
        writeFile(recentFile(userId), list.take(recentCapacity))
    }

    //reemplaza las recientes con las que se juntaron con la nube
    @Synchronized
    fun setRecentCookies(userId: String, cookies: List<FortuneModel>) {
        writeFile(recentFile(userId), cookies.take(recentCapacity))
    }

    //frases traidas del api que estan esperando en el telefono
    @Synchronized
    fun getDeck(): List<FortuneModel> = readFile(deckFile)

    //agrega frases nuevas al final sin repetir ninguna
    @Synchronized
    fun appendDeck(newCookies: List<FortuneModel>) {
        val deck = readFile(deckFile).toMutableList()
        for (cookie in newCookies) {
            if (deck.none { it.quote == cookie.quote }) deck.add(cookie)
        }
        writeFile(deckFile, deck)
    }

    //saca del disco la frase que se abrio, las demas se quedan
    @Synchronized
    fun removeFromDeck(cookieId: Int) {
        writeFile(deckFile, readFile(deckFile).filter { it.id != cookieId })
    }

    //frases que vienen dentro de la app en assets/fortune_seed.json, solo para cuando no hay nada mas
    fun getSeeds(): List<FortuneModel> {
        return try {
            val text = context.assets.open("fortune_seed.json").bufferedReader().use { it.readText() }
            fromJson(text)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
