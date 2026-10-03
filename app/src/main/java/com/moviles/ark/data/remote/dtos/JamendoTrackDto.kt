package com.moviles.ark.data.remote.dtos

//dto para representar una cancion individual devuelta por el api de jamendo (#7)
data class JamendoTrackDto(
    val id: String = "",
    val name: String = "",
    val duration: Int = 0,
    val artistName: String = "",
    val audioUrl: String = "",
    val albumImage: String = ""
)
