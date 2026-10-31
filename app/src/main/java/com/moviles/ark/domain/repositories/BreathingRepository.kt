package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.AmbientTrackCatalog
import com.moviles.ark.domain.models.AmbientTrackModel
import com.moviles.ark.domain.models.BreathingSession

//repositorio de dominio para la herramienta de respiracion guiada
interface BreathingRepository {
    //pista predeterminada que viene dentro de la app: se puede reproducir de una vez, con o sin internet
    val defaultTrack: AmbientTrackModel?
        get() = null

    //obtiene las pistas de audio ambiental (desde jamendo o, sin conexion, las guardadas en cache)
    suspend fun getAmbientTracks(): AmbientTrackCatalog

    //uri para reproducir la pista: el archivo en cache si existe o la url remota para empezar por streaming
    suspend fun resolveTrackAudio(track: AmbientTrackModel): String = track.audioUrl

    //guarda la pista en la cache del disco para que quede disponible sin internet; null si no hacia falta o fallo
    suspend fun saveTrackForOffline(track: AmbientTrackModel): AmbientTrackModel? = null

    //ids de las pistas que hoy suenan sin internet (la de la app y las que siguen en la cache del disco)
    suspend fun offlineTrackIds(): Set<String> = emptySet()

    //guarda en el telefono una sesion terminada y su ToolInteractionModel; funciona sin internet
    suspend fun saveSession(session: BreathingSession): Result<Unit>
}
