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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

//lee la imagen de una galleta y la decodifica para pintarla; orden de lectura: memoria (LruCache) -> filesDir
//solo las 10 galletas del dia se quedan en memoria; las demas se decodifican cada vez que se abren, pero
//reutilizando la memoria del bitmap de la ultima foto que se cerro (inBitmap) en vez de pedir uno nuevo cada vez
//decodificar es trabajo de cpu: va en Dispatchers.Default; leer el archivo va en Dispatchers.IO (dentro del store)
class CookieImageLoaderImpl(
    private val context: Context,
    private val imageStore: CookiePrivateImageStore,
    private val memoryCache: CookieMemoryCache = CookieMemoryCache(),
) : CookieImageLoader<Bitmap> {

    //ids de las galletas del jarro de hoy: las unicas que se guardan en la cache de memoria
    @Volatile private var dailyIds: Set<String> = emptySet()

    //bitmap de una foto que ya se cerro (no esta en pantalla ni en la cache); la proxima foto que no es del jarro
    //de hoy se decodifica encima de el. solo se guarda uno: en el detalle se ve una foto a la vez
    private var reusableBitmap: Bitmap? = null

    override suspend fun load(cookie: CookieModel): Bitmap? {
        if (!cookie.hasImage) return null
        memoryCache.get(cookie.id)?.let { return it }
        if (!cookie.hasLocalImage()) return null
        val started = System.nanoTime()
        val bytes = imageStore.readBytes(cookie.localImagePath) ?: return null
        val isInTodaysJar = cookie.id in dailyIds
        //las del jarro de hoy se quedan en la cache: esas nunca se reutilizan, se decodifican normal
        val bitmap = if (isInTodaysJar) decode(bytes) else decodeReusingMemory(bytes)
        if (bitmap == null) return null
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        if (isInTodaysJar) {
            memoryCache.put(cookie.id, bitmap)
            Log.d(CookieMemoryCache.TAG, "disk read id=${cookie.id} in $elapsedMs ms, kept in memory (today's jar)")
        } else {
            Log.d(CookieMemoryCache.TAG, "disk read id=${cookie.id} in $elapsedMs ms, not kept (not in today's jar)")
        }
        return bitmap
    }

    //el detalle se cerro y ya no muestra esta imagen: si es de las que no van a la cache se guarda para reutilizarla
    //las de la cache son inmutables y las de decodeReusingMemory son mutables, asi se distinguen
    override fun release(image: Bitmap) {
        if (!image.isMutable || image.isRecycled) return
        synchronized(this) {
            reusableBitmap = image
        }
    }

    //saca el bitmap guardado (y lo deja en null) para que dos decodificaciones nunca usen el mismo
    private fun takeReusableBitmap(): Bitmap? {
        synchronized(this) {
            val bitmap = reusableBitmap
            reusableBitmap = null
            return bitmap
        }
    }

    //decodifica en paralelo las del jarro de hoy que aun no estan en memoria: cada async lee su archivo en
    //Dispatchers.IO y lo decodifica en Dispatchers.Default (dentro de load), asi el hilo principal solo espera
    //supervisorScope + try/catch: un archivo danado o que se borro en medio no cancela las otras ni tumba la pantalla
    override suspend fun prefetch(dailyCookies: List<CookieModel>) {
        dailyIds = dailyCookies.map { it.id }.toSet()
        val missing = dailyCookies.filter { it.hasLocalImage() && !memoryCache.contains(it.id) }
        if (missing.isEmpty()) return
        Log.d(CookieMemoryCache.TAG, "prefetch ${missing.size} pictures of today's jar")
        supervisorScope {
            val decodes = mutableListOf<Deferred<Bitmap?>>()
            for (cookie in missing) {
                decodes.add(async { loadOrSkip(cookie) })
            }
            decodes.awaitAll()
        }
    }

    //como load, pero si esa imagen falla se salta (null) en vez de lanzar el error
    private suspend fun loadOrSkip(cookie: CookieModel): Bitmap? {
        try {
            return load(cookie)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(CookieMemoryCache.TAG, "prefetch failed id=${cookie.id}: ${e.javaClass.simpleName}")
            return null
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
        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetSidePx)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    //como decode, pero los pixeles se escriben en la memoria del bitmap de la ultima foto cerrada (inBitmap)
    //asi abrir galletas que no son del jarro de hoy no pide ~1,5 mb nuevos cada vez (los viejos quedaban en ram hasta el gc)
    private suspend fun decodeReusingMemory(bytes: ByteArray): Bitmap? = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, DISPLAY_SIDE_PX)

        val options = BitmapFactory.Options()
        options.inSampleSize = sample
        //inBitmap solo funciona con bitmaps mutables, y el resultado tambien queda mutable
        options.inMutable = true
        val oldBitmap = takeReusableBitmap()
        if (oldBitmap != null && fitsIn(oldBitmap, bounds.outWidth, bounds.outHeight, sample)) {
            options.inBitmap = oldBitmap
        }
        try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: IllegalArgumentException) {
            //android no pudo usar la memoria vieja: se decodifica en un bitmap nuevo
            options.inBitmap = null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }

    //la imagen nueva cabe en la memoria del bitmap viejo (4 bytes por pixel en ARGB_8888)
    private fun fitsIn(oldBitmap: Bitmap, width: Int, height: Int, sample: Int): Boolean {
        if (oldBitmap.isRecycled || oldBitmap.config != Bitmap.Config.ARGB_8888) return false
        //se redondea hacia arriba para no quedarse corto
        val newWidth = (width + sample - 1) / sample
        val newHeight = (height + sample - 1) / sample
        val bytesNeeded = newWidth.toLong() * newHeight * 4
        return bytesNeeded <= oldBitmap.allocationByteCount
    }

    //el inSampleSize mas grande (potencia de 2) que deja el lado mayor en al menos targetSidePx
    private fun sampleSizeFor(width: Int, height: Int, targetSidePx: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= targetSidePx) sample *= 2
        return sample
    }

    companion object {
        //lado mayor minimo con el que se decodifica para el detalle: una imagen de 1440 px baja a 720 (inSampleSize 2)
        //y ocupa ~1,5 mb en ram en vez de ~6 mb, asi las 10 del dia caben en 1/8 de la memoria de la app
        const val DISPLAY_SIDE_PX = 720
        //la vista previa del formulario es pequena
        const val PREVIEW_SIDE_PX = 480
    }
}
