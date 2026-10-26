package com.moviles.ark.data.remote.apis

import android.content.Context
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.adapters.MusicServiceAdapter
import com.moviles.ark.domain.models.AmbientTrackModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

//adapter para el servicio de musica de jamendo con cache temporal en disco (cacheDir/audio_cache)
//evita retener audios en ram para prevenir oom y permite reproducir y listar canciones en cache sin conexion
class JamendoServiceAdapterImpl(
    private val context: Context,
    private val apiClient: JamendoApiClient = JamendoApiClient(),
    private val bundledAudioUri: String = ""
) : MusicServiceAdapter {

    //cache en disco; cacheDir es el estandar oficial de android para audios descargados bajo demanda con politica lru
    private val audioCacheDir = File(context.cacheDir, "audio_cache").apply {
        if (!exists()) mkdirs()
    }

    override suspend fun getAmbientTracks(limit: Int): Result<List<AmbientTrackModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val dtos = try {
                apiClient.fetchAmbientTracks(limit)
            } catch (e: Exception) {
                emptyList()
            }

            if (dtos.isNotEmpty()) {
                dtos.map { dto ->
                    saveTrackMetadata(dto.id, dto.name, dto.artistName, dto.duration, dto.albumImage.ifBlank { null })
                    val cachedFile = File(audioCacheDir, "${dto.id}.mp3")
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
            } else {
                //cuando la api de jamendo no responde o no hay internet, lista todos los mp3s guardados en disco
                listCachedTracks()
            }
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal("JamendoServiceAdapter", "getAmbientTracks", error)
        }
    }

    override suspend fun resolveTrackAudioUri(trackId: String, remoteAudioUrl: String): String = withContext(Dispatchers.IO) {
        val cachedFile = File(audioCacheDir, "$trackId.mp3")
        if (cachedFile.exists() && cachedFile.length() > 0L) {
            cachedFile.setLastModified(System.currentTimeMillis())
            return@withContext cachedFile.absolutePath
        }

        //si no esta en disco y viene por url remota, se descarga por streams a filesDir sin retener bytes en memoria ram
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
                    val tempFile = File(audioCacheDir, "$trackId.tmp")
                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    if (tempFile.length() > 0L && tempFile.renameTo(cachedFile)) {
                        return@withContext cachedFile.absolutePath
                    }
                }
            } catch (e: Exception) {
                CrashlyticsHelper.logNonFatal("JamendoServiceAdapter", "resolveTrackAudioUri_download", e, mapOf("track_id" to trackId))
            }
        }

        if (bundledAudioUri.isNotBlank()) return@withContext bundledAudioUri
        return@withContext remoteAudioUrl
    }

    //lista todas las canciones descargadas que residen fisicamente en disco para que el usuario pueda reproducirlas offline
    private fun listCachedTracks(): List<AmbientTrackModel> {
        val mp3Files = audioCacheDir.listFiles()?.filter { it.extension == "mp3" && it.length() > 0L } ?: return emptyList()
        return mp3Files.map { file ->
            val trackId = file.nameWithoutExtension
            val meta = readTrackMetadata(trackId)
            val fallback = KNOWN_TRACKS.find { it.id == trackId }
            AmbientTrackModel(
                id = trackId,
                title = meta?.title ?: fallback?.title ?: "Cached Track $trackId",
                artist = meta?.artist ?: fallback?.artist ?: "Jamendo (offline)",
                audioUrl = file.absolutePath,
                durationSeconds = meta?.duration ?: fallback?.durationSeconds ?: 180,
                coverImageUrl = meta?.coverUrl ?: fallback?.coverImageUrl,
                isAvailableOffline = true
            )
        }
    }

    private fun saveTrackMetadata(trackId: String, title: String, artist: String, duration: Int, coverUrl: String?) {
        try {
            val json = JSONObject().put("title", title).put("artist", artist).put("duration", duration).put("coverUrl", coverUrl ?: "")
            File(audioCacheDir, "$trackId.meta").writeText(json.toString())
        } catch (_: Exception) { }
    }

    private fun readTrackMetadata(trackId: String): TrackMeta? {
        return try {
            val file = File(audioCacheDir, "$trackId.meta")
            if (!file.exists()) return null
            val json = JSONObject(file.readText())
            TrackMeta(
                title = json.optString("title", "Cached Track $trackId"),
                artist = json.optString("artist", "Jamendo (offline)"),
                duration = json.optInt("duration", 180),
                coverUrl = json.optString("coverUrl").ifBlank { null }
            )
        } catch (_: Exception) { null }
    }

    //politica lru en disco para no saturar el almacenamiento
    private fun pruneDiskCacheIfNeeded() {
        val files = audioCacheDir.listFiles()?.filter { it.extension == "mp3" } ?: return
        if (files.size >= MAX_CACHED_TRACKS || files.sumOf { it.length() } > MAX_CACHE_SIZE_BYTES) {
            val sortedByOldest = files.sortedBy { it.lastModified() }
            val filesToDelete = sortedByOldest.take(files.size - MAX_CACHED_TRACKS + 1)
            for (file in filesToDelete) {
                file.delete()
                File(audioCacheDir, "${file.nameWithoutExtension}.meta").delete()
            }
        }
    }

    private data class TrackMeta(val title: String, val artist: String, val duration: Int, val coverUrl: String?)

    companion object {
        private const val MAX_CACHED_TRACKS = 6
        private const val MAX_CACHE_SIZE_BYTES = 25 * 1024 * 1024L

        //pistas conocidas de respaldo para resolver nombres offline si el archivo meta no existe
        private val KNOWN_TRACKS = listOf(
            AmbientTrackModel("16398", "Eau bénite et feu sacré", "Philippe Mangold", "", 245, "https://usercontent.jamendo.com?type=album&id=2334&width=300&trackid=16398", true),
            AmbientTrackModel("1985894", "432 Hz Meditation with Theta Waves", "Gaia Meditation", "", 300, "https://usercontent.jamendo.com?type=album&id=502461&width=300&trackid=1985894", true),
            AmbientTrackModel("1699096", "Gratitude (Full Track)", "AudioSphere", "", 244, "https://usercontent.jamendo.com?type=album&id=439588&width=300&trackid=1699096", true),
            AmbientTrackModel("1920257", "Winter Dream", "Nargo", "", 297, "https://usercontent.jamendo.com?type=album&id=471628&width=300&trackid=1920257", true),
            AmbientTrackModel("1906544", "Ambient Harp", "Raw Vibrations", "", 396, "https://usercontent.jamendo.com?type=album&id=465310&width=300&trackid=1906544", true),
            AmbientTrackModel("1705713", "Blissful Sky", "AudioSphere", "", 124, "https://usercontent.jamendo.com?type=album&id=439700&width=300&trackid=1705713", true)
        )
    }
}
