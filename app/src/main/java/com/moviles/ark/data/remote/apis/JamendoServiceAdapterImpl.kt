package com.moviles.ark.data.remote.apis

import android.content.Context
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.adapters.MusicServiceAdapter
import com.moviles.ark.domain.models.AmbientTrackModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

//adapter para el servicio de musica de jamendo con cache temporal en disco (cacheDir/audio_cache)
//evita retener audios en ram para prevenir oom y permite reproducir y listar canciones en cache sin conexion
class JamendoServiceAdapterImpl(
    private val context: Context,
    private val apiClient: JamendoApiClient = JamendoApiClient()
) : MusicServiceAdapter {

    //cache en disco; cacheDir es el estandar oficial de android para audios descargados bajo demanda con politica lru
    private val audioCacheDir = File(context.cacheDir, "audio_cache").apply {
        if (!exists()) mkdirs()
    }

    //ids que se estan descargando, para no bajar dos veces la misma pista al mismo tiempo sobre el mismo archivo
    private val downloadsInProgress = mutableSetOf<String>()

    override suspend fun getAmbientTracks(limit: Int): Result<List<AmbientTrackModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val dtos = apiClient.fetchAmbientTracks(limit)
            //ni con los reintentos llegaron pistas: se trata igual que sin conexion
            if (dtos.isEmpty()) throw IOException("jamendo no devolvio pistas")

            dtos.map { dto ->
                AmbientTrackModel(
                    id = dto.id,
                    title = dto.name,
                    artist = dto.artistName,
                    //se guarda siempre la url remota; si la pista ya esta en cache se reproduce desde el archivo
                    audioUrl = dto.audioUrl,
                    durationSeconds = dto.duration,
                    coverImageUrl = dto.albumImage.ifBlank { null },
                    isAvailableOffline = cachedFileOf(dto.id).isUsable()
                )
            }
        }.onFailure { error ->
            //sin internet es un caso esperado; solo se reportan los errores raros (por ejemplo json invalido)
            if (error !is IOException) CrashlyticsHelper.logNonFatal("JamendoServiceAdapter", "getAmbientTracks", error)
        }
    }

    override suspend fun getCachedTracks(): List<AmbientTrackModel> = withContext(Dispatchers.IO) {
        listCachedTracks()
    }

    override suspend fun resolvePlaybackUri(track: AmbientTrackModel): String = withContext(Dispatchers.IO) {
        val cachedFile = cachedFileOf(track.id)
        if (cachedFile.isUsable()) {
            //se marca como usada para que la limpieza lru borre primero las que no se escuchan
            cachedFile.setLastModified(System.currentTimeMillis())
            cachedFile.absolutePath
        } else {
            //sin archivo local se empieza de una vez por streaming; la descarga para offline va aparte
            track.audioUrl
        }
    }

    override suspend fun cacheTrack(track: AmbientTrackModel): AmbientTrackModel? = withContext(Dispatchers.IO) {
        val cachedFile = cachedFileOf(track.id)
        if (cachedFile.isUsable()) return@withContext track.copy(isAvailableOffline = true)
        if (!track.audioUrl.startsWith("https://") && !track.audioUrl.startsWith("http://")) return@withContext null
        //si esa pista ya se esta descargando no se abre otra descarga que escriba el mismo archivo
        val isFirstDownload = synchronized(downloadsInProgress) { downloadsInProgress.add(track.id) }
        if (!isFirstDownload) return@withContext null

        //se descarga a un archivo .part y solo se renombra a .mp3 cuando llego completo
        val partFile = File(audioCacheDir, "${track.id}.part")
        try {
            val connection = (URL(track.audioUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 15000
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val expectedBytes = connection.contentLengthLong
                //un archivo mas grande que toda la cache borraria las demas pistas: no se guarda
                if (expectedBytes > MAX_CACHE_SIZE_BYTES) return@withContext null
                pruneDiskCacheIfNeeded(incomingBytes = expectedBytes.coerceAtLeast(0L))

                //se copia por bloques sin retener el audio en ram; si se sale de la pantalla la descarga se corta aqui
                connection.inputStream.use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }

                //un archivo cortado sonaria unos segundos y se detendria: solo entra a la cache si llego completo
                val isComplete = partFile.length() > 0L && (expectedBytes <= 0L || partFile.length() == expectedBytes)
                if (isComplete && partFile.renameTo(cachedFile)) {
                    saveTrackMetadata(track)
                    track.copy(isAvailableOffline = true)
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal("JamendoServiceAdapter", "cacheTrack_download", e, mapOf("track_id" to track.id))
            null
        } finally {
            //si la descarga fallo o se cancelo no queda el archivo a medias
            partFile.delete()
            synchronized(downloadsInProgress) { downloadsInProgress.remove(track.id) }
        }
    }

    private fun cachedFileOf(trackId: String) = File(audioCacheDir, "$trackId.mp3")

    private fun File.isUsable() = exists() && length() > 0L

    //lista todas las canciones descargadas que residen fisicamente en disco para que el usuario pueda reproducirlas offline
    private fun listCachedTracks(): List<AmbientTrackModel> {
        val mp3Files = audioCacheDir.listFiles()?.filter { it.extension == "mp3" && it.length() > 0L } ?: return emptyList()
        //las escuchadas mas recientemente van primero
        return mp3Files.sortedByDescending { it.lastModified() }.map { file ->
            val trackId = file.nameWithoutExtension
            val meta = readTrackMetadata(trackId)
            val fallback = KNOWN_TRACKS.find { it.id == trackId }
            AmbientTrackModel(
                id = trackId,
                title = meta?.title ?: fallback?.title ?: "Saved track",
                artist = meta?.artist ?: fallback?.artist ?: "Jamendo",
                audioUrl = meta?.audioUrl ?: file.absolutePath,
                durationSeconds = meta?.duration ?: fallback?.durationSeconds ?: 0,
                coverImageUrl = meta?.coverUrl ?: fallback?.coverImageUrl,
                isAvailableOffline = true
            )
        }
    }

    //los datos de la pista se guardan junto al mp3 para mostrar su nombre cuando no hay internet
    private fun saveTrackMetadata(track: AmbientTrackModel) {
        try {
            val json = JSONObject()
                .put("title", track.title)
                .put("artist", track.artist)
                .put("duration", track.durationSeconds)
                .put("coverUrl", track.coverImageUrl ?: "")
                .put("audioUrl", track.audioUrl)
            File(audioCacheDir, "${track.id}.meta").writeText(json.toString())
        } catch (_: Exception) { }
    }

    private fun readTrackMetadata(trackId: String): TrackMeta? {
        return try {
            val file = File(audioCacheDir, "$trackId.meta")
            if (!file.exists()) return null
            val json = JSONObject(file.readText())
            TrackMeta(
                title = json.optString("title").ifBlank { "Saved track" },
                artist = json.optString("artist").ifBlank { "Jamendo" },
                duration = json.optInt("duration", 0),
                coverUrl = json.optString("coverUrl").ifBlank { null },
                audioUrl = json.optString("audioUrl").ifBlank { null }
            )
        } catch (_: Exception) { null }
    }

    //politica lru en disco para no saturar el almacenamiento: borra las mas viejas hasta que quepa la nueva
    private fun pruneDiskCacheIfNeeded(incomingBytes: Long) {
        val allFiles = audioCacheDir.listFiles() ?: return
        val mp3Ids = allFiles.filter { it.extension == "mp3" }.map { it.nameWithoutExtension }.toSet()
        val activeDownloads = synchronized(downloadsInProgress) { downloadsInProgress.toSet() }
        //restos: descargas a medias que ya no siguen (.tmp, .part) y datos de pistas que nunca se descargaron
        allFiles.filter { file ->
            when (file.extension) {
                "tmp" -> true
                "part" -> file.nameWithoutExtension !in activeDownloads
                "meta" -> file.nameWithoutExtension !in mp3Ids
                else -> false
            }
        }.forEach { it.delete() }

        val mp3Files = allFiles.filter { it.extension == "mp3" }.sortedBy { it.lastModified() }.toMutableList()
        var totalBytes = mp3Files.sumOf { it.length() }
        while (mp3Files.isNotEmpty() && (mp3Files.size >= MAX_CACHED_TRACKS || totalBytes + incomingBytes > MAX_CACHE_SIZE_BYTES)) {
            val oldest = mp3Files.removeAt(0)
            totalBytes -= oldest.length()
            oldest.delete()
            File(audioCacheDir, "${oldest.nameWithoutExtension}.meta").delete()
        }
    }

    private data class TrackMeta(val title: String, val artist: String, val duration: Int, val coverUrl: String?, val audioUrl: String?)

    companion object {
        private const val MAX_CACHED_TRACKS = 6
        private const val MAX_CACHE_SIZE_BYTES = 25 * 1024 * 1024L
        private const val BUFFER_SIZE = 64 * 1024

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
