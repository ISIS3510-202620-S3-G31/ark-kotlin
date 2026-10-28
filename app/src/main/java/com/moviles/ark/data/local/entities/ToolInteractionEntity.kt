package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moviles.ark.domain.models.ToolInteractionModel
import java.util.Date

//tabla de interacciones con herramientas: una fila cada vez que el usuario usa una herramienta
//los atributos siguen la clase ToolInteractionModel del diagrama de clases
//la usan las estadisticas, la recomendacion por frecuencia y la telemetria
@Entity(tableName = "tool_interactions")
data class ToolInteractionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    //usuario que uso la herramienta (User.id; relacion userId del diagrama)
    val userId: String,
    //herramienta usada (ToolModel.id; relacion idTool del diagrama), por ejemplo "custom_breathing"
    val toolId: String,
    //cuanto duro, en segundos
    val duration: Int,
    //como interactuo: "voice", "text", "touch" o "photo"
    val interactionType: String,
    //intensidad que registro la herramienta, por ejemplo el volumen del grito; 0 si no aplica
    val intensity: Int = 0,
    //true si el usuario termino la herramienta
    val complete: Boolean,
    //resultado numerico de la herramienta, por ejemplo cuantos ciclos de respiracion; 0 si no aplica
    val value: Int = 0,
    //cuando ocurrio; room la guarda como milisegundos con el conversor de Date (Converters)
    val timestamp: Date,
    //detalle opcional
    val description: String = "",
    //false mientras no se haya subido a firestore (#33)
    val isSynced: Boolean = false,
)

//de fila de la tabla a modelo de dominio
fun ToolInteractionEntity.toDomain(): ToolInteractionModel {
    return ToolInteractionModel(
        userId = userId,
        toolId = toolId,
        duration = duration,
        interactionType = interactionType,
        intensity = intensity,
        complete = complete,
        value = value,
        timestamp = timestamp,
        description = description,
    )
}

//de modelo de dominio a fila de la tabla
fun ToolInteractionModel.toEntity(): ToolInteractionEntity {
    return ToolInteractionEntity(
        userId = userId,
        toolId = toolId,
        duration = duration,
        interactionType = interactionType,
        intensity = intensity,
        complete = complete,
        value = value,
        timestamp = timestamp,
        description = description,
    )
}
