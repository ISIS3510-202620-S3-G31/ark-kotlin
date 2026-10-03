package com.moviles.ark.domain.models

//modelo de dominio para las canciones de musica ambiental de las sesiones de respiracion (#7)
data class AmbientTrackModel(
    val id: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val durationSeconds: Int = 0,
    val coverImageUrl: String? = null,
    //true si la pista viene dentro de la app (res/raw) y suena sin internet (#82)
    val isAvailableOffline: Boolean = false
)
