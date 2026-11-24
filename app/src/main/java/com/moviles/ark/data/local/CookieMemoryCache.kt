package com.moviles.ark.data.local

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache

//cache en memoria (ram) de las imagenes ya decodificadas de las 10 galletas del dia
//LruCache propia en vez de glide: asi se controla que se guarda (solo las del jarro de hoy) y cuanto ocupa
//el tamano se mide en kb (bitmap.byteCount / 1024) y el maximo es 1/8 de la memoria que android le da a la app;
//si se llena, sale la imagen que hace mas tiempo no se usa (least recently used)
class CookieMemoryCache(
    maxSizeKb: Int = defaultMaxSizeKb(),
) {
    //cuantas veces se encontro la imagen en ram y cuantas hubo que ir al disco; se ven en logcat
    @Volatile private var hits = 0
    @Volatile private var misses = 0

    private val cache = object : LruCache<String, Bitmap>(maxSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)

        //evicted = true cuando la saco la lru por falta de espacio; false si se reemplazo o se quito a mano
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) Log.d(TAG, "memory evict id=$key (${oldValue.byteCount / 1024} KB) -> ${sizeKb()} / ${maxSizeKb()} KB")
        }
    }

    fun get(cookieId: String): Bitmap? {
        val bitmap = cache.get(cookieId)
        if (bitmap != null) {
            hits++
            Log.d(TAG, "memory hit id=$cookieId (hits=$hits, misses=$misses)")
        } else {
            misses++
            Log.d(TAG, "memory miss id=$cookieId (hits=$hits, misses=$misses)")
        }
        return bitmap
    }

    fun contains(cookieId: String): Boolean = cache.get(cookieId) != null

    fun put(cookieId: String, bitmap: Bitmap) {
        cache.put(cookieId, bitmap)
        Log.d(TAG, "memory put id=$cookieId (${bitmap.byteCount / 1024} KB) -> ${sizeKb()} / ${maxSizeKb()} KB")
    }

    fun remove(cookieId: String) {
        cache.remove(cookieId)
    }

    fun sizeKb(): Int = cache.size()

    fun maxSizeKb(): Int = cache.maxSize()

    companion object {
        const val TAG = "CookieCache"

        //1/8 de la memoria maxima de la app, en kb
        fun defaultMaxSizeKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    }
}
