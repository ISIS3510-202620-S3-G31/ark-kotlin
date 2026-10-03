package com.moviles.ark.data.remote.dtos

//dto para serializar y transferir registros de fotos en firebase firestore (#28, #31)
data class PhotoEntryDto(
    val id: String = "",
    val userId: String = "",
    val caption: String = "",
    val takenAt: Long = 0L,
    val remoteUrl: String = ""
)
