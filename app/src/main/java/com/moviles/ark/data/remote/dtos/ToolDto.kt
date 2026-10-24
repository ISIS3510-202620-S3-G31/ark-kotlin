package com.moviles.ark.data.remote.dtos

import com.moviles.ark.data.local.entities.ToolEntity
import com.moviles.ark.domain.models.Tool
import com.moviles.ark.domain.models.ToolCategory

//dto del documento tools/{toolId} en firestore (#120)
//el catalogo es de solo lectura para la app (las reglas no dejan escribir); sirve para refreshTools cuando el catalogo venga de firestore
data class ToolDto(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    //id de ToolCategory, por ejemplo "calm_down"
    val category: String = "",
    //"voice", "text", "touch" o "photo"
    val format: String = "",
    val iconName: String = ""
)

//de modelo de dominio a dto
fun Tool.toDto(): ToolDto {
    return ToolDto(
        id = id,
        name = name,
        description = description,
        category = category.id,
        format = format,
        iconName = iconName
    )
}

//de dto a modelo de dominio
fun ToolDto.toDomain(): Tool {
    return Tool(
        id = id,
        name = name,
        description = description,
        category = ToolCategory.fromId(category),
        format = format,
        iconName = iconName
    )
}

//de dto a fila de room, para guardar el catalogo en el telefono
fun ToolDto.toEntity(): ToolEntity {
    return ToolEntity(
        id = id,
        name = name,
        description = description,
        category = category,
        format = format,
        iconName = iconName
    )
}

//de fila de room a dto
fun ToolEntity.toDto(): ToolDto {
    return ToolDto(
        id = id,
        name = name,
        description = description,
        category = category,
        format = format,
        iconName = iconName
    )
}
