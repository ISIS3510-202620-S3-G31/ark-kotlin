package com.moviles.ark.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.moviles.ark.domain.exceptions.InvalidCookieImageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

//comprime la imagen de una galleta una sola vez, al crearla: lado mayor 1440 px y jpeg calidad 85 (~300-500 kb)
//esa misma version se guarda en privado y es la que se sube a la nube; asi nunca se comprime dos veces
//corre en Dispatchers.Default porque decodificar y comprimir es trabajo de cpu
class CookieImageCompressor(private val context: Context) {

    suspend fun compress(sourceUri: String): ByteArray = withContext(Dispatchers.Default) {
        val uri = Uri.parse(sourceUri)
        val resolver = context.contentResolver
        //primero solo el tamano, sin cargar los pixeles
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        //con inJustDecodeBounds decodeStream siempre devuelve null: el tamano queda en bounds
        val boundsStream = runCatching { resolver.openInputStream(uri) }.getOrNull() ?: throw InvalidCookieImageException()
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw InvalidCookieImageException()

        //inSampleSize en potencias de 2 sin bajar de 1440 px: carga menos pixeles a la ram
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw InvalidCookieImageException()

        //una sola matriz endereza la foto segun el exif de la camara y la reduce al lado mayor de 1440
        val matrix = orientationMatrix(uri)
        val largestSide = maxOf(decoded.width, decoded.height)
        if (largestSide > MAX_SIDE_PX) {
            val scale = MAX_SIDE_PX.toFloat() / largestSide
            matrix.postScale(scale, scale)
        }
        val finalBitmap = if (matrix.isIdentity) decoded else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }
        if (finalBitmap != decoded) decoded.recycle()

        val output = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        finalBitmap.recycle()
        output.toByteArray()
    }

    //la camara guarda la foto "acostada" y anota en el exif como girarla
    private fun orientationMatrix(uri: Uri): Matrix {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
    }

    companion object {
        const val MAX_SIDE_PX = 1440
        const val JPEG_QUALITY = 85
    }
}
