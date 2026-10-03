package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

//tabla de interacciones con herramientas: una fila cada vez que el usuario usa una herramienta
//la usan las estadisticas (#15), la recomendacion por frecuencia (#22), los insights (#28) y la bq del #34
@Entity(tableName = "tool_interactions")
data class ToolInteractionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    //id de la herramienta, el mismo de ToolEntity (por ejemplo "custom_breathing")
    val toolId: String,
    //"voice", "text", "touch" o "photo", copiado de la herramienta al momento de usarla
    val format: String,
    //cuando empezo, en milisegundos
    val startedAt: Long,
    val durationSeconds: Int,
    //false mientras no se haya subido a firestore
    val isSynced: Boolean = false,
)
