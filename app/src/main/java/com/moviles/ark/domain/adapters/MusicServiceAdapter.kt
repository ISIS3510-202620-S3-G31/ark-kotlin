package com.moviles.ark.domain.adapters

import com.moviles.ark.domain.models.AmbientTrackModel

//adapter del servicio de musica ambiental para desacoplar jamendo del repositorio de respiracion
//permite obtener pistas de audio y guardarlas en la cache del disco para reproducirlas offline
interface MusicServiceAdapter {
    //obtiene las pistas relajantes del proveedor; falla si no responde (por ejemplo sin internet)
    suspend fun getAmbientTracks(limit: Int = 10): Result<List<AmbientTrackModel>>

    //pistas ya descargadas en la cache del disco: son las unicas del proveedor que suenan sin conexion
    suspend fun getCachedTracks(): List<AmbientTrackModel>

    //uri para empezar a sonar de una vez: el archivo en cache si ya existe; si no, la url remota por streaming
    suspend fun resolvePlaybackUri(track: AmbientTrackModel): String

    //descarga la pista completa a la cache para que quede disponible sin conexion
    //devuelve la pista apuntando al archivo local, o null si no se pudo guardar
    suspend fun cacheTrack(track: AmbientTrackModel): AmbientTrackModel?
}
