package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.ToolInteraction

//interacciones con herramientas guardadas en el telefono (#82, #96)
//el dominio no sabe que detras esta room; la implementacion vive en data
interface ToolInteractionRepository {
    //interacciones desde una fecha en milisegundos, de la mas vieja a la mas nueva
    suspend fun getInteractionsSince(fromMillis: Long): Result<List<ToolInteraction>>
}
