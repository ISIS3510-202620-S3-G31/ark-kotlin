package com.moviles.ark.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.moviles.ark.data.local.daos.BreathingSessionDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.toEntity
import com.moviles.ark.domain.adapters.MusicServiceAdapter
import com.moviles.ark.domain.models.AmbientTrackCatalog
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

    //pista guardada dentro de la app: es la unica predeterminada y suena aunque no haya internet
    override val defaultTrack: AmbientTrackModel = AmbientTrackModel(
        id = BUNDLED_TRACK_ID,
        title = "Calm Waves",
        artist = "Ark",
        audioUrl = bundledAudioUri,
        durationSeconds = 60,
        isAvailableOffline = true
    )

    override suspend fun getAmbientTracks(): AmbientTrackCatalog {
        val jamendoTracks = musicServiceAdapter.getAmbientTracks(limit = JAMENDO_TRACK_LIMIT).getOrNull().orEmpty()
        return if (jamendoTracks.isNotEmpty()) {
            //con internet: la pista de la app primero y luego las de jamendo (las ya descargadas van marcadas offline)
            AmbientTrackCatalog(tracks = (listOf(defaultTrack) + jamendoTracks).distinctBy { it.id }, isOnline = true)
        } else {
            //sin respuesta de jamendo solo se ofrece lo que suena offline: la pista de la app y las guardadas en cache
            val cachedTracks = musicServiceAdapter.getCachedTracks()
            AmbientTrackCatalog(tracks = (listOf(defaultTrack) + cachedTracks).distinctBy { it.id }, isOnline = false)
        }
    }

    override suspend fun resolveTrackAudio(track: AmbientTrackModel): String {
        if (track.id == BUNDLED_TRACK_ID) return track.audioUrl
        return musicServiceAdapter.resolvePlaybackUri(track)
    }

    //la pista de la app ya esta en el telefono; las de jamendo se descargan a la cache del disco
    override suspend fun saveTrackForOffline(track: AmbientTrackModel): AmbientTrackModel? {
        if (track.id == BUNDLED_TRACK_ID) return null
        return musicServiceAdapter.cacheTrack(track)
    }

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
        private const val JAMENDO_TRACK_LIMIT = 10
    }
}
