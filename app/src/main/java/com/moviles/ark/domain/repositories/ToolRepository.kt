package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.ToolModel
import kotlinx.coroutines.flow.Flow

//catalogo de herramientas con room como unica fuente de verdad (single source of truth)
//la ui solo escucha getTools(); refreshTools() trae lo nuevo de firestore y lo guarda en room,
//y como getTools() lee de room, la pantalla se actualiza sola sin pedir nada mas
interface ToolRepository {
    //emite el catalogo guardado en el telefono y vuelve a emitir cada vez que cambia
    fun getTools(): Flow<List<ToolModel>>

    //una sola lectura del catalogo guardado en el telefono (sin red); failure si no se pudo leer
    suspend fun getLocalTools(): Result<List<ToolModel>>

    //busca una herramienta en el catalogo local, null si no existe
    suspend fun getToolById(id: String): ToolModel?

    //descarga el catalogo de firestore y actualiza room
    //sin internet devuelve failure y la ui sigue mostrando lo que ya estaba guardado
    suspend fun refreshTools(): Result<Unit>

    //guarda en room y en el archivo interno un catalogo que ya llego (por ejemplo en un push) sin ir a la red
    suspend fun saveCatalog(tools: List<ToolModel>): Result<Unit>
}
