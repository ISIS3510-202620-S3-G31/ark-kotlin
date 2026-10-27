package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moviles.ark.domain.models.FeedbackModel

//tabla de calificaciones que deja el usuario al terminar una herramienta (#21)
@Entity(tableName = "tool_feedback")
data class FeedbackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    //interaccion calificada (id de ToolInteractionEntity); null si no se guardo la interaccion
    val interactionId: Long? = null,
    val toolId: String,
    //de 1 a 5 (estrellas o emojis)
    val rating: Int,
    //comentario opcional
    val comment: String = "",
    //fecha en milisegundos
    val createdAt: Long,
    //false mientras no se haya subido a firestore (#33)
    val isSynced: Boolean = false,
)

//de fila de Room a modelo de dominio (#166)
fun FeedbackEntity.toDomain(): FeedbackModel {
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

//de modelo de dominio a fila de Room (#166)
fun FeedbackModel.toEntity(): FeedbackEntity {
    return FeedbackEntity(
        id = id,
        interactionId = interactionId,
        toolId = toolId,
        rating = rating,
        comment = comment,
        createdAt = createdAt,
        isSynced = isSynced
    )
}
