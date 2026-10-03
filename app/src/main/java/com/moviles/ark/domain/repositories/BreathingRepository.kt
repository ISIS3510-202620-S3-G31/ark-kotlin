package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.AmbientTrackModel

//repositorio de dominio para la herramienta de respiracion guiada (#7, #8)
interface BreathingRepository {
    //obtiene las pistas de audio ambiental para las sesiones de respiracion (desde jamendo o canciones de respaldo)
    suspend fun getAmbientTracks(): Result<List<AmbientTrackModel>>
}
