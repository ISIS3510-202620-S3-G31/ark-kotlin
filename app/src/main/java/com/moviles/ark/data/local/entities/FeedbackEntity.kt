package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

//tabla de calificaciones que deja el usuario al terminar una herramienta
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
    //false mientras no se haya subido a firestore
    val isSynced: Boolean = false,
)
