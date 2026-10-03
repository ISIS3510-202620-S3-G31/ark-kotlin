package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moviles.ark.domain.models.Tool
import com.moviles.ark.domain.models.ToolCategory

@Entity(tableName = "tools")
data class ToolEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val format: String,
    val iconName: String,
)

// Extension functions to map between ToolEntity and Tool domain model
fun ToolEntity.toDomain(): Tool {
    return Tool(
        id = id,
        name = name,
        description = description,
        category = ToolCategory.fromId(category),
        format = format,
        iconName = iconName,
    )
}

fun Tool.toEntity(): ToolEntity {
    return ToolEntity(
        id = id,
        name = name,
        description = description,
        category = category.id,
        format = format,
        iconName = iconName,
    )
}

