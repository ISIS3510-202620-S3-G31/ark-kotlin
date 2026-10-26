package com.moviles.ark.data.remote.apis

import android.content.Context
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.adapters.MusicServiceAdapter
import com.moviles.ark.domain.models.AmbientTrackModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

//adapter para el servicio de musica de jamendo con cache temporal exclusivamente en disco (cacheDir)
//evita retener archivos de audio en memoria ram para prevenir sobrecarga de memoria (oom) en el dispositivo
class JamendoServiceAdapterImpl(
    private val context: Context,
    private val apiClient: JamendoApiClient = JamendoApiClient(),
    private val bundledAudioUri: String = ""
) : MusicServiceAdapter {

    //cache temporal en disco; android puede purgar cacheDir si el sistema requiere espacio
    private val cacheDir = File(context.cacheDir, "audio_cache").apply {
        if (!exists()) mkdirs()
    }

    override suspend fun getAmbientTracks(limit: Int): Result<List<AmbientTrackModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val dtos = apiClient.fetchAmbientTracks(limit)
            dtos.map { dto ->
                val cachedFile = File(cacheDir, "${dto.id}.mp3")
                val isCached = cachedFile.exists() && cachedFile.length() > 0L
                AmbientTrackModel(
                    id = dto.id,
                    title = dto.name,
                    artist = dto.artistName,
                    audioUrl = if (isCached) cachedFile.absolutePath else dto.audioUrl,
                    durationSeconds = dto.duration,
                    coverImageUrl = dto.albumImage.ifBlank { null },
                    isAvailableOffline = isCached
                )
            }
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "JamendoServiceAdapter",
                action = "getAmbientTracks",
                throwable = error
            )
        }
    }

    override suspend fun resolveTrackAudioUri(trackId: String, remoteAudioUrl: String): String = withContext(Dispatchers.IO) {
        //si ya esta en la cache de disco y no ha sido purgada, se usa directamente
        val cachedFile = File(cacheDir, "$trackId.mp3")
        if (cachedFile.exists() && cachedFile.length() > 0L) {
            cachedFile.setLastModified(System.currentTimeMillis())
            return@withContext cachedFile.absolutePath
        }

        //si no esta en disco y viene por url remota, se descarga por streams directamente a disco sin cargar bytes en ram
        if (remoteAudioUrl.startsWith("http://") || remoteAudioUrl.startsWith("https://")) {
            try {
                val url = URL(remoteAudioUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 12000
                    instanceFollowRedirects = true
                }
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    pruneDiskCacheIfNeeded()

                    val tempFile = File(cacheDir, "$trackId.tmp")
                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.length() > 0L && tempFile.renameTo(cachedFile)) {
                        return@withContext cachedFile.absolutePath
                    }
                }
            } catch (e: Exception) {
                CrashlyticsHelper.logNonFatal(
                    componentName = "JamendoServiceAdapter",
                    action = "resolveTrackAudioUri_download",
                    throwable = e,
                    extraKeys = mapOf("track_id" to trackId)
                )
            }
        }

        //si fallo la descarga o no hay conexion y la pista no estaba en cache de disco,
        //hace fallback inmediato a la pista bundled fija de la app (storage nativo)
        if (bundledAudioUri.isNotBlank()) {
            return@withContext bundledAudioUri
        }

        return@withContext remoteAudioUrl
    }

    //politica lru en disco para no saturar el almacenamiento temporal
    private fun pruneDiskCacheIfNeeded() {
        val files = cacheDir.listFiles()?.filter { it.extension == "mp3" } ?: return
        if (files.size >= MAX_CACHED_TRACKS || files.sumOf { it.length() } > MAX_CACHE_SIZE_BYTES) {
            val sortedByOldest = files.sortedBy { it.lastModified() }
            val filesToDelete = sortedByOldest.take(files.size - MAX_CACHED_TRACKS + 1)
            for (file in filesToDelete) {
                file.delete()
            }
        }
    }

    companion object {
        //maximo 5 canciones en cache temporal de disco
        private const val MAX_CACHED_TRACKS = 5
        //tamano maximo de 20MB para la cache temporal en disco
        private const val MAX_CACHE_SIZE_BYTES = 20 * 1024 * 1024L
    }
}
