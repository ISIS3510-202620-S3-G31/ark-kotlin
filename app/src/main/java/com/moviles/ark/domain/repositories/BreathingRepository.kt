package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.AmbientTrackModel
import com.moviles.ark.domain.models.BreathingSession

//repositorio de dominio para la herramienta de respiracion guiada
interface BreathingRepository {
    //obtiene las pistas de audio ambiental para las sesiones de respiracion (desde jamendo o canciones de respaldo)
    suspend fun getAmbientTracks(): Result<List<AmbientTrackModel>>

    //resuelve la uri local de la pista si esta en cache o la descarga para que quede lista sin internet
    suspend fun resolveTrackAudio(track: AmbientTrackModel): String = track.audioUrl

    //guarda en el telefono una sesion terminada y su ToolInteractionModel; funciona sin internet
    suspend fun saveSession(session: BreathingSession): Result<Unit>
}
