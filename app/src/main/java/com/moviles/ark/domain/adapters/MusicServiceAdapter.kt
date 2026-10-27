package com.moviles.ark.domain.adapters

import com.moviles.ark.domain.models.AmbientTrackModel

//adapter del servicio de musica ambiental para desacoplar jamendo del repositorio de respiracion
//permite obtener pistas de audio y resolverlas desde la cache local en disco para reproduccion offline
interface MusicServiceAdapter {
    //obtiene las pistas relajantes del proveedor o pistas cacheadas/offline
    suspend fun getAmbientTracks(limit: Int = 10): Result<List<AmbientTrackModel>>

    //resuelve la uri de reproduccion: si el archivo mp3 ya esta en el almacenamiento interno lo usa directo;
    //si es remoto intenta descargarlo para que quede disponible sin conexion y devuelve su ruta local
    suspend fun resolveTrackAudioUri(trackId: String, remoteAudioUrl: String): String
}
