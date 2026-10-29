package com.moviles.ark.data.remote.dtos

import com.moviles.ark.data.local.entities.FeedbackEntity
import com.moviles.ark.domain.models.FeedbackModel

//dto del documento users/{uid}/tool_feedback/{id} en firestore
//la calificacion que deja el usuario al terminar una herramienta
data class FeedbackDto(
    val userId: String = "",
    val toolId: String = "",
    //de 1 a 5
    val rating: Int = 0,
    val comment: String = "",
    //fecha en milisegundos
    val createdAt: Long = 0L,
    //id local de la interaccion calificada; null si no se guardo la interaccion
    val interactionId: Long? = null
)

//de fila de room a dto
fun FeedbackEntity.toDto(userId: String): FeedbackDto {
    return FeedbackDto(
        userId = userId,
        toolId = toolId,
        rating = rating,
        comment = comment,
        createdAt = createdAt,
        interactionId = interactionId
    )
}

//id del documento en firestore: siempre el mismo para la misma fila, asi subirla dos veces no la duplica
fun FeedbackEntity.documentId(): String = "${createdAt}_$toolId"

//de dto a fila de room; ya viene de firestore, asi que esta sincronizado
fun FeedbackDto.toEntity(): FeedbackEntity {
    return FeedbackEntity(
        interactionId = interactionId,
        toolId = toolId,
        rating = rating,
        comment = comment,
        createdAt = createdAt,
        isSynced = true
    )
}

//de modelo de dominio a dto para Firestore
fun FeedbackModel.toDto(userId: String): FeedbackDto {
    return FeedbackDto(
        userId = userId,
        toolId = toolId,
        rating = rating,
        comment = comment,
        createdAt = createdAt,
        interactionId = interactionId
    )
}

//de dto de Firestore a modelo de dominio
fun FeedbackDto.toDomain(id: Long = 0L, isSynced: Boolean = true): FeedbackModel {
    return FeedbackModel(
        id = id,
        interactionId = interactionId,
        toolId = toolId,
        rating = rating,
        comment = comment,
        createdAt = createdAt,
        isSynced = isSynced
    )
}
