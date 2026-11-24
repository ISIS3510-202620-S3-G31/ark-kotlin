package com.moviles.ark.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.moviles.ark.domain.adapters.CookieImageLoader
import com.moviles.ark.domain.models.CookieModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

//lee la imagen de una galleta desde el almacenamiento privado y la decodifica para pintarla
//decodificar es trabajo de cpu: va en Dispatchers.Default; leer el archivo va en Dispatchers.IO (dentro del store)
class CookieImageLoaderImpl(
    private val context: Context,
    private val imageStore: CookiePrivateImageStore,
) : CookieImageLoader<Bitmap> {

    override suspend fun load(cookie: CookieModel): Bitmap? {
        if (!cookie.hasLocalImage()) return null
        val bytes = imageStore.readBytes(cookie.localImagePath) ?: return null
        return decode(bytes)
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
        //lado mayor con el que se muestra la imagen en el detalle
        const val DISPLAY_SIDE_PX = 1080
        //la vista previa del formulario es pequena
        const val PREVIEW_SIDE_PX = 480
    }
}
