package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.ToolInteractionModel

//interacciones con herramientas: las pendientes en el telefono y las ya subidas a la nube
//el dominio no sabe que detras estan room y firestore; la implementacion vive en data
interface ToolInteractionRepository {
    //interacciones desde una fecha en milisegundos, de la mas vieja a la mas nueva
    suspend fun getInteractionsSince(fromMillis: Long): Result<List<ToolInteractionModel>>

    //guarda que el usuario con sesion uso una herramienta y devuelve el id local de la interaccion
    //complete = false cuando la abandono antes de terminarla
    suspend fun recordCompletion(
        toolId: String,
        durationSeconds: Int,
        interactionType: String,
        timestamp: Long,
        complete: Boolean = true
    ): Result<Long>
}
