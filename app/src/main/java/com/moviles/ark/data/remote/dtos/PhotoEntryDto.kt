package com.moviles.ark.data.remote.dtos

import com.google.firebase.firestore.Exclude
import com.moviles.ark.data.local.entities.PhotoEntryEntity
import com.moviles.ark.domain.models.PhotoEntryModel

//dto para serializar y transferir registros de fotos en firebase firestore (#28, #31)
//documento users/{uid}/photos/{id} (#163)
data class PhotoEntryDto(
    //id del documento en firestore; no se guarda como campo porque firestore ya lo tiene
    @get:Exclude
    val id: String = "",
    val userId: String = "",
    val caption: String = "",
    val takenAt: Long = 0L,
    //ruta de la foto en el telefono que la tomo
    val localFilePath: String = "",
    val remoteUrl: String = ""
)

//de fila de room a dto, para subir las fotos guardadas sin conexion (#33)
fun PhotoEntryEntity.toDto(userId: String): PhotoEntryDto {
    return PhotoEntryDto(
        userId = userId,
        caption = caption,
        takenAt = takenAt,
        localFilePath = localFilePath,
        remoteUrl = remoteUrl.orEmpty()
    )
}

//de modelo de dominio a dto
fun PhotoEntryModel.toDto(userId: String): PhotoEntryDto {
    return PhotoEntryDto(
        userId = userId,
        caption = caption,
        takenAt = takenAt,
        localFilePath = localFilePath
    )
}

//de dto a modelo de dominio; el id local no viaja a firestore, asi que queda en 0
fun PhotoEntryDto.toDomain(): PhotoEntryModel {
    return PhotoEntryModel(
        localFilePath = localFilePath,
        caption = caption,
        takenAt = takenAt
    )
}

//de dto a fila de room; ya viene de firestore, asi que esta sincronizada
fun PhotoEntryDto.toEntity(): PhotoEntryEntity {
    return PhotoEntryEntity(
        localFilePath = localFilePath,
        caption = caption,
        takenAt = takenAt,
        remoteUrl = remoteUrl.ifEmpty { null },
        isSynced = true
    )
}
