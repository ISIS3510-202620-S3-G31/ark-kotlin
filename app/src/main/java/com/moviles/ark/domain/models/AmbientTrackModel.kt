package com.moviles.ark.domain.models

//modelo de dominio para las canciones de musica ambiental de las sesiones de respiracion
data class AmbientTrackModel(
    val id: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val durationSeconds: Int = 0,
    val coverImageUrl: String? = null,
    //true si la pista suena sin internet: viene dentro de la app (res/raw) o ya esta descargada en la cache
    val isAvailableOffline: Boolean = false
) {
    //con internet suena cualquier pista; sin internet solo la de la app y las descargadas,
    //las demas solo existen en jamendo y pedirlas por streaming sin red solo daria error
    fun isPlayable(isOnline: Boolean): Boolean = isOnline || isAvailableOffline
}

//lista de musica para la hoja: la pista de la app primero y luego las de jamendo o las guardadas en cache
data class AmbientTrackCatalog(
    val tracks: List<AmbientTrackModel>,
    //false si jamendo no respondio (por ejemplo sin internet): solo se ofrecen las pistas que suenan offline
    val isOnline: Boolean
)
