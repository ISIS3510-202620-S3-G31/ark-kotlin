package com.moviles.ark.data.remote.dtos

import com.moviles.ark.data.local.entities.ToolInteractionEntity
import com.moviles.ark.domain.models.ToolInteractionModel
import java.util.Date

//dto del documento users/{uid}/tool_interactions/{id} en firestore
//mismos campos que ToolInteractionModel del diagrama de clases; stats y la pregunta de negocio #4 los leen por nombre
data class ToolInteractionDto(
    val userId: String = "",
    val toolId: String = "",
    //cuando ocurrio; firestore lo guarda como Timestamp
    val timestamp: Date = Date(0),
    //cuanto duro, en segundos
    val duration: Int = 0,
    val interactionType: String = "",
    val intensity: Int = 0,
    val complete: Boolean = false,
    val value: Int = 0,
    val description: String = ""
)

//de fila de room a dto; el userId es el del usuario con sesion, igual que antes en el sync
fun ToolInteractionEntity.toDto(userId: String): ToolInteractionDto {
    return ToolInteractionDto(
        userId = userId,
        toolId = toolId,
        timestamp = timestamp,
        duration = duration,
        interactionType = interactionType,
        intensity = intensity,
        complete = complete,
        value = value,
        description = description
    )
}

//de modelo de dominio a dto
fun ToolInteractionModel.toDto(): ToolInteractionDto {
    return ToolInteractionDto(
        userId = userId,
        toolId = toolId,
        timestamp = timestamp,
        duration = duration,
        interactionType = interactionType,
        intensity = intensity,
        complete = complete,
        value = value,
        description = description
    )
}

//de dto a modelo de dominio
fun ToolInteractionDto.toDomain(): ToolInteractionModel {
    return ToolInteractionModel(
        userId = userId,
        toolId = toolId,
        duration = duration,
        interactionType = interactionType,
        intensity = intensity,
        complete = complete,
        value = value,
        timestamp = timestamp,
        description = description
    )
}

//de dto a fila de room; ya viene de firestore, asi que esta sincronizada
fun ToolInteractionDto.toEntity(): ToolInteractionEntity {
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
        isSynced = true
    )
}
