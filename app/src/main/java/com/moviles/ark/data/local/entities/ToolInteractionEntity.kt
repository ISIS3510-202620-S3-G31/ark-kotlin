package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moviles.ark.domain.models.ToolInteraction
import java.util.Date

//tabla de interacciones con herramientas: una fila cada vez que el usuario usa una herramienta
//los atributos siguen la clase ToolInteraction del diagrama de clases
//la usan las estadisticas (#15), la recomendacion por frecuencia (#22), los insights (#28) y la bq del #34
@Entity(tableName = "tool_interactions")
data class ToolInteractionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    //usuario que uso la herramienta (User.id; relacion userId del diagrama)
    val userId: String,
    //herramienta usada (Tool.id; relacion idTool del diagrama), por ejemplo "custom_breathing"
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
fun ToolInteractionEntity.toDomain(): ToolInteraction {
    return ToolInteraction(
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
fun ToolInteraction.toEntity(): ToolInteractionEntity {
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
