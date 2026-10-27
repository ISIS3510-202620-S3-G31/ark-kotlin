package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.ToolInteractionModel

//interacciones con herramientas guardadas en el telefono
//el dominio no sabe que detras esta room; la implementacion vive en data
interface ToolInteractionRepository {
    //interacciones desde una fecha en milisegundos, de la mas vieja a la mas nueva
    suspend fun getInteractionsSince(fromMillis: Long): Result<List<ToolInteractionModel>>

    //guarda que el usuario con sesion termino una herramienta y devuelve el id local de la interaccion
    suspend fun recordCompletion(toolId: String, durationSeconds: Int, interactionType: String, timestamp: Long): Result<Long>
}
