package com.moviles.ark.data.local

import com.moviles.ark.domain.exceptions.NotEnoughSpaceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

//aqui se guarda en almacenamiento privado: las imagenes del cookie jar son archivos filesDir/cookies/{id}.jpg
//solo la app los puede leer (no van a la galeria ni piden permisos); room guarda la ruta en la tabla cookies
//esta clase es la unica que toca esos archivos: guardar, leer, borrar (tambien al desalojar) y medir cuanto ocupan
class CookiePrivateImageStore(filesDir: File) {

    private val directory = File(filesDir, DIR_NAME)

    //escribe primero {id}.jpg.tmp y luego lo renombra: si la app se cierra a mitad no queda un jpg danado
    //sin espacio lanza NotEnoughSpaceException y no deja nada escrito
    suspend fun save(cookieId: String, jpegBytes: ByteArray): String = withContext(Dispatchers.IO) {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create the cookies folder")
        //se deja un margen libre para no llenar el telefono hasta el ultimo byte
        if (directory.usableSpace < jpegBytes.size + MIN_FREE_BYTES) throw NotEnoughSpaceException()
        val tempFile = File(directory, "${fileNameOf(cookieId)}$TEMP_SUFFIX")
        try {
            tempFile.writeBytes(jpegBytes)
        } catch (e: IOException) {
            tempFile.delete()
            //ENOSPC: se lleno el disco mientras se escribia
            if (e.message?.contains("ENOSPC") == true || directory.usableSpace < jpegBytes.size) throw NotEnoughSpaceException(e)
            throw e
        }
        val target = fileOf(cookieId)
        if (!tempFile.renameTo(target)) {
            tempFile.delete()
            throw IOException("Could not save the picture of the cookie")
        }
        target.absolutePath
    }

    //el archivo de una ruta guardada en room, solo si sigue existiendo y es de esta carpeta
    suspend fun existingFile(path: String?): File? = withContext(Dispatchers.IO) {
        if (path == null) return@withContext null
        val file = File(path)
        file.takeIf { it.parentFile == directory && it.isFile && it.length() > 0L }
    }

    suspend fun readBytes(path: String?): ByteArray? = withContext(Dispatchers.IO) {
        existingFile(path)?.readBytes()
    }

    //borra la imagen de una galleta; true si ya no queda el archivo
    suspend fun delete(cookieId: String): Boolean = withContext(Dispatchers.IO) {
        val file = fileOf(cookieId)
        File(directory, "${fileNameOf(cookieId)}$TEMP_SUFFIX").delete()
        !file.exists() || file.delete()
    }

    //borra solo el temporal de una galleta (una descarga o una escritura que no termino)
    suspend fun deleteTemp(cookieId: String) = withContext(Dispatchers.IO) {
        File(directory, "${fileNameOf(cookieId)}$TEMP_SUFFIX").delete()
        Unit
    }

    //bytes que ocupan todas las imagenes de galletas en el telefono
    suspend fun usedBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles()?.filter { it.isFile && it.name.endsWith(JPG_SUFFIX) }?.sumOf { it.length() } ?: 0L
    }

    //bytes de la imagen de una galleta; 0 si no esta
    suspend fun sizeOf(cookieId: String): Long = withContext(Dispatchers.IO) {
        fileOf(cookieId).takeIf { it.isFile }?.length() ?: 0L
    }

    //espacio libre del almacenamiento interno, para elegir el presupuesto de imagenes
    suspend fun usableSpace(): Long = withContext(Dispatchers.IO) {
        if (directory.exists()) directory.usableSpace else directory.parentFile?.usableSpace ?: 0L
    }

    //restos de escrituras cortadas (.tmp) que quedaron si la app se cerro a mitad de guardar
    suspend fun deleteLeftovers() = withContext(Dispatchers.IO) {
        directory.listFiles()?.filter { it.name.endsWith(TEMP_SUFFIX) }?.forEach { it.delete() }
        Unit
    }

    private fun fileOf(cookieId: String) = File(directory, fileNameOf(cookieId))

    //el id es un uuid: no puede traer separadores ni puntos, para no salirse de la carpeta
    private fun fileNameOf(cookieId: String): String {
        require(cookieId.isNotBlank() && cookieId.all { it.isLetterOrDigit() || it == '-' }) { "Invalid cookie id" }
        return "$cookieId$JPG_SUFFIX"
    }

    companion object {
        const val DIR_NAME = "cookies"
        private const val JPG_SUFFIX = ".jpg"
        private const val TEMP_SUFFIX = ".tmp"
        //10 mb libres de margen al guardar una imagen
        private const val MIN_FREE_BYTES = 10L * 1024 * 1024
    }
}
