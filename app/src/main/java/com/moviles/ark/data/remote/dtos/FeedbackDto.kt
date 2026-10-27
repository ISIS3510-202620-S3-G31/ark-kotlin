package com.moviles.ark.data.remote.dtos

import com.moviles.ark.data.local.entities.FeedbackEntity
import com.moviles.ark.domain.models.FeedbackModel

//dto del documento users/{uid}/tool_feedback/{id} en firestore (#163)
//la calificacion que deja el usuario al terminar una herramienta (#21)
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

//de modelo de dominio a dto para Firestore (#166)
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

//de dto de Firestore a modelo de dominio aplicando reglas de negocio (#166)
fun FeedbackDto.toDomain(id: Long = 0L, isSynced: Boolean = true): FeedbackModel {
    val safeRating = if (FeedbackModel.isValidRating(rating)) rating else FeedbackModel.MIN_RATING
    val safeToolId = toolId.ifBlank { "unknown_tool" }
    val safeCreatedAt = if (createdAt > 0L) createdAt else System.currentTimeMillis()
    val safeInteractionId = if (interactionId != null && interactionId > 0L) interactionId else null
    return FeedbackModel(
        id = id,
        interactionId = safeInteractionId,
        toolId = safeToolId,
        rating = safeRating,
        comment = comment,
        createdAt = safeCreatedAt,
        isSynced = isSynced
    )
}
