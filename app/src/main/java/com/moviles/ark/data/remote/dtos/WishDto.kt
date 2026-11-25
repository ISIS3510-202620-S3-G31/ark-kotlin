package com.moviles.ark.data.remote.dtos

import com.google.firebase.firestore.Exclude
import com.moviles.ark.data.local.entities.WishEntity

//como se guarda un deseo en firestore
//documento users/{uid}/wishes/{id}; el id del documento es el mismo uuid de room
//lo usan el sync (para subir) y la recuperacion (para bajar) en eventual connectivity
data class WishDto(
    //id del documento; no se guarda como campo porque firestore ya lo tiene
    @get:Exclude
    val id: String = "",
    val userId: String = "",
    val text: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

//de fila de room a dto, para subir el deseo
fun WishEntity.toDto(): WishDto {
    return WishDto(
        id = id,
        userId = userId,
        text = text,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

//de dto a fila de room, para guardar un deseo que se bajo de firestore
//ya viene de la nube, asi que esta sincronizado y no esta borrado
fun WishDto.toEntity(): WishEntity {
    return WishEntity(
        id = id,
        userId = userId,
        text = text,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSynced = true,
        isDeleted = false
    )
}
