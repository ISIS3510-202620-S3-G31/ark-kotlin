package com.moviles.ark.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moviles.ark.domain.composite.MoodFactory
import com.moviles.ark.domain.composite.SingleEmotion
import com.moviles.ark.domain.models.FeedbackModel

//tabla de calificaciones que deja el usuario al terminar una herramienta
@Entity(tableName = "tool_feedback")
data class FeedbackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    //cuenta que dejo el feedback; el sync solo lo sube a esa cuenta aunque se cierre sesion y entre otra
    //"" en filas de antes de la version 9 (se suben a la cuenta abierta, como antes)
    @ColumnInfo(defaultValue = "")
    val userId: String = "",
    //interaccion calificada (id de ToolInteractionEntity); null si no se guardo la interaccion
    val interactionId: Long? = null,
    val toolId: String,
    //de 1 a 5 (estrellas o emojis)
    val rating: Int,
    //comentario opcional
    val comment: String = "",
    //emociones del feedback con su intensidad ("HAPPINESS:4"), igual que en mood_checkins
    //el default es el de la columna que agrega la migracion de la version 8
    @ColumnInfo(defaultValue = "[]")
    val emotions: List<String> = emptyList(),
    //fecha en milisegundos
    val createdAt: Long,
    //false mientras no se haya subido a firestore
    val isSynced: Boolean = false,
)

//de fila de Room a modelo de dominio
fun FeedbackEntity.toDomain(): FeedbackModel {
    val safeRating = if (FeedbackModel.ratingError(rating) == null) rating else FeedbackModel.MIN_RATING
    val safeToolId = toolId.ifBlank { "unknown_tool" }
    val safeCreatedAt = if (createdAt > 0L) createdAt else System.currentTimeMillis()
    val safeInteractionId = if (interactionId != null && interactionId > 0L) interactionId else null
    return FeedbackModel(
        id = id,
        interactionId = safeInteractionId,
        toolId = safeToolId,
        rating = safeRating,
        comment = comment,
        emotions = MoodFactory.decodeParts(emotions, SingleEmotion.DEFAULT_INTENSITY),
        createdAt = safeCreatedAt,
        isSynced = isSynced
    )
}

//de modelo de dominio a fila de Room
fun FeedbackModel.toEntity(): FeedbackEntity {
    return FeedbackEntity(
        id = id,
        interactionId = interactionId,
        toolId = toolId,
        rating = rating,
        comment = comment,
        emotions = MoodFactory.encodeParts(emotions),
        createdAt = createdAt,
        isSynced = isSynced
    )
}
