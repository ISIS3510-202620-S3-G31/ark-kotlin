package com.moviles.ark.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.moviles.ark.domain.adapters.CookieImageLoader
import com.moviles.ark.domain.models.CookieModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

//lee la imagen de una galleta y la decodifica para pintarla; orden de lectura: memoria (LruCache) -> filesDir
//solo las 10 galletas del dia se quedan en memoria; las demas se decodifican cada vez que se abren
//decodificar es trabajo de cpu: va en Dispatchers.Default; leer el archivo va en Dispatchers.IO (dentro del store)
class CookieImageLoaderImpl(
    private val context: Context,
    private val imageStore: CookiePrivateImageStore,
    private val memoryCache: CookieMemoryCache = CookieMemoryCache(),
) : CookieImageLoader<Bitmap> {

    //ids de las galletas del jarro de hoy: las unicas que se guardan en la cache de memoria
    @Volatile private var dailyIds: Set<String> = emptySet()

    override suspend fun load(cookie: CookieModel): Bitmap? {
        if (!cookie.hasImage) return null
        memoryCache.get(cookie.id)?.let { return it }
        if (!cookie.hasLocalImage()) return null
        val started = System.nanoTime()
        val bitmap = imageStore.readBytes(cookie.localImagePath)?.let { decode(it) } ?: return null
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        if (cookie.id in dailyIds) {
            memoryCache.put(cookie.id, bitmap)
            Log.d(CookieMemoryCache.TAG, "disk read id=${cookie.id} in $elapsedMs ms, kept in memory (today's jar)")
        } else {
            Log.d(CookieMemoryCache.TAG, "disk read id=${cookie.id} in $elapsedMs ms, not kept (not in today's jar)")
        }
        return bitmap
    }

    //decodifica en paralelo (Dispatchers.Default) las del jarro de hoy que aun no estan en memoria
    override suspend fun prefetch(dailyCookies: List<CookieModel>) {
        dailyIds = dailyCookies.map { it.id }.toSet()
        val missing = dailyCookies.filter { it.hasLocalImage() && !memoryCache.contains(it.id) }
        if (missing.isEmpty()) return
        Log.d(CookieMemoryCache.TAG, "prefetch ${missing.size} pictures of today's jar")
        coroutineScope {
            missing.map { cookie -> async { load(cookie) } }.awaitAll()
        }
    }

    override fun forget(cookieId: String) {
        memoryCache.remove(cookieId)
    }

    override suspend fun loadPreview(sourceUri: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { it.readBytes() }
        }.getOrNull()
    }?.let { bytes -> decode(bytes, PREVIEW_SIDE_PX)?.let { uprightPreview(it, bytes) } }

    //la foto de la camara viene "acostada": se gira segun el exif como se hara al guardarla
    private suspend fun uprightPreview(bitmap: Bitmap, bytes: ByteArray): Bitmap = withContext(Dispatchers.Default) {
        val degrees = runCatching {
            when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return@withContext bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        if (rotated != bitmap) bitmap.recycle()
        rotated
    }

    //inSampleSize en potencias de 2 sin bajar del tamano pedido: menos ram por imagen
    private suspend fun decode(bytes: ByteArray, targetSidePx: Int = DISPLAY_SIDE_PX): Bitmap? = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetSidePx) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        //lado mayor minimo con el que se decodifica para el detalle: una imagen de 1440 px baja a 720 (inSampleSize 2)
        //y ocupa ~1,5 mb en ram en vez de ~6 mb, asi las 10 del dia caben en 1/8 de la memoria de la app
        const val DISPLAY_SIDE_PX = 720
        //la vista previa del formulario es pequena
        const val PREVIEW_SIDE_PX = 480
    }
}
