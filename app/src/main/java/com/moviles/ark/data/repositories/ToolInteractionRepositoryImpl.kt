package com.moviles.ark.data.repositories

import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.toDomain
import com.moviles.ark.domain.models.ToolInteraction
import com.moviles.ark.domain.repositories.ToolInteractionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

//lee las interacciones de la tabla tool_interactions de room (#96); funciona sin internet
class ToolInteractionRepositoryImpl(
    private val toolRecordDao: ToolRecordDao
) : ToolInteractionRepository {

    override suspend fun getInteractionsSince(fromMillis: Long): Result<List<ToolInteraction>> = withContext(Dispatchers.IO) {
        runCatching {
            //first(): solo se necesita la foto actual de la tabla, no seguir escuchando cambios
            toolRecordDao.getInteractionsSince(fromMillis).first().map { it.toDomain() }
        }
    }
}
