package com.moviles.ark.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.moviles.ark.data.local.daos.BreathingSessionDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.toEntity
import com.moviles.ark.domain.adapters.MusicServiceAdapter
import com.moviles.ark.domain.models.AmbientTrackModel
import com.moviles.ark.domain.models.BreathingSession
import com.moviles.ark.domain.repositories.BreathingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

//implementacion del repositorio de respiracion que utiliza el adapter de musica para desacoplar el proveedor
//y guarda las sesiones terminadas en room para que funcione sin internet
class BreathingRepositoryImpl(
    private val breathingSessionDao: BreathingSessionDao,
    private val toolRecordDao: ToolRecordDao,
    private val auth: FirebaseAuth,
    //direccion android.resource:// de la pista que viene dentro de la app (res/raw)
    bundledAudioUri: String,
    private val musicServiceAdapter: MusicServiceAdapter,
    //avisa al SyncManager que hay una interaccion por subir; no se espera
    private val onInteractionSaved: () -> Unit = {}
) : BreathingRepository {

    //pista guardada dentro de la app: suena aunque no haya internet
    private val bundledTrack = AmbientTrackModel(
        id = BUNDLED_TRACK_ID,
        title = "Calm Waves",
        artist = "Ark (offline)",
        audioUrl = bundledAudioUri,
        durationSeconds = 60,
        isAvailableOffline = true
    )

    //pistas de respaldo precargadas por si no hay internet o falla la conexion
    private val fallbackTracks = listOf(
        AmbientTrackModel(
            id = "16398",
            title = "Eau bénite et feu sacré",
            artist = "Philippe Mangold",
            durationSeconds = 245,
            audioUrl = "https://prod-1.storage.jamendo.com/?trackid=16398&format=mp32&from=HlL4l9QaCPjx43C3x5c7IQ%3D%3D%7CwgxG1iCePB53BExtHdxvsA%3D%3D",
            coverImageUrl = "https://usercontent.jamendo.com?type=album&id=2334&width=300&trackid=16398"
        ),
        AmbientTrackModel(
            id = "1985894",
            title = "432 Hz Meditation with Theta Waves",
            artist = "Gaia Meditation",
            durationSeconds = 300,
            audioUrl = "https://prod-1.storage.jamendo.com/?trackid=1985894&format=mp32&from=XZWQY%2BAsQQCAH4x5ehQWCw%3D%3D%7CHbpFh54VH%2FN%2BchW905lRfA%3D%3D",
            coverImageUrl = "https://usercontent.jamendo.com?type=album&id=502461&width=300&trackid=1985894"
        ),
        AmbientTrackModel(
            id = "1699096",
            title = "Gratitude (Full Track)",
            artist = "AudioSphere",
            durationSeconds = 244,
            audioUrl = "https://prod-1.storage.jamendo.com/?trackid=1699096&format=mp32&from=AhQmKwTTI4egZk6tAmoRBw%3D%3D%7Ct%2Bn7F0e4kZyAWPC8zQYsZw%3D%3D",
            coverImageUrl = "https://usercontent.jamendo.com?type=album&id=439588&width=300&trackid=1699096"
        ),
        AmbientTrackModel(
            id = "1920257",
            title = "Winter Dream",
            artist = "Nargo",
            durationSeconds = 297,
            audioUrl = "https://prod-1.storage.jamendo.com/?trackid=1920257&format=mp32&from=I1%2F1C4VTD3tupTXbJ7i5rg%3D%3D%7CK0sJxXnQzhk7zxH5o3GFlw%3D%3D",
            coverImageUrl = "https://usercontent.jamendo.com?type=album&id=471628&width=300&trackid=1920257"
        ),
        AmbientTrackModel(
            id = "1906544",
            title = "Ambient Harp",
            artist = "Raw Vibrations",
            durationSeconds = 396,
            audioUrl = "https://prod-1.storage.jamendo.com/?trackid=1906544&format=mp32&from=LrwbzCN2oTZG2CmaYnrKsg%3D%3D%7CZIWfstqvGvX2e1VdFeswVw%3D%3D",
            coverImageUrl = "https://usercontent.jamendo.com?type=album&id=465310&width=300&trackid=1906544"
        ),
        AmbientTrackModel(
            id = "1705713",
            title = "Blissful Sky",
            artist = "AudioSphere",
            durationSeconds = 124,
            audioUrl = "https://prod-1.storage.jamendo.com/?trackid=1705713&format=mp32&from=un%2BPPB%2BRmu%2FfPWfDjAW00A%3D%3D%7CYfi6obcT5egK%2BmEsECshRA%3D%3D",
            coverImageUrl = "https://usercontent.jamendo.com?type=album&id=439700&width=300&trackid=1705713"
        )
    )

    override suspend fun getAmbientTracks(): Result<List<AmbientTrackModel>> {
        val remoteResult = musicServiceAdapter.getAmbientTracks(limit = 10)
        return if (remoteResult.isSuccess && remoteResult.getOrNull()?.isNotEmpty() == true) {
            val tracks = remoteResult.getOrNull().orEmpty()
            //con internet o cache: primero las pistas del adapter y al final la pista predeterminada de la app
            Result.success(tracks + bundledTrack)
        } else {
            Result.success(offlineTracks())
        }
    }

    override suspend fun resolveTrackAudio(track: AmbientTrackModel): String {
        if (track.id == BUNDLED_TRACK_ID) return track.audioUrl
        return musicServiceAdapter.resolveTrackAudioUri(track.id, track.audioUrl)
    }

    //sin respuesta de jamendo (por ejemplo sin internet): primero la pista de la app, que siempre suena
    private fun offlineTracks(): List<AmbientTrackModel> = listOf(bundledTrack)

    override suspend fun saveSession(session: BreathingSession): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            //breathing_sessions solo guarda las terminadas; la interaccion se guarda siempre, con complete = true / false
            if (session.isComplete()) breathingSessionDao.insertSession(session.toEntity())
            val userId = auth.currentUser?.uid ?: "anonymous"
            toolRecordDao.insertInteraction(session.toInteraction(userId).toEntity())
            onInteractionSaved()
            Unit
        }
    }

    companion object {
        const val BUNDLED_TRACK_ID = "bundled_calm_waves"
    }
}

