package com.moviles.ark.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.ToolInteractionEntity
import com.moviles.ark.data.local.entities.toDomain
import com.moviles.ark.domain.models.ToolInteractionModel
import com.moviles.ark.domain.repositories.ToolInteractionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Date

//lee las interacciones de la tabla tool_interactions de room; funciona sin internet
class ToolInteractionRepositoryImpl(
    private val toolRecordDao: ToolRecordDao,
    //sin sesion la interaccion se guarda como "anonymous"
    private val auth: FirebaseAuth? = null
) : ToolInteractionRepository {

    override suspend fun getInteractionsSince(fromMillis: Long): Result<List<ToolInteractionModel>> = withContext(Dispatchers.IO) {
        runCatching {
            //first(): solo se necesita la foto actual de la tabla, no seguir escuchando cambios
            toolRecordDao.getInteractionsSince(fromMillis).first().map { it.toDomain() }
        }
    }

    override suspend fun recordCompletion(
        toolId: String,
        durationSeconds: Int,
        interactionType: String,
        timestamp: Long
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            toolRecordDao.insertInteraction(
                ToolInteractionEntity(
                    userId = auth?.currentUser?.uid ?: "anonymous",
                    toolId = toolId,
                    duration = durationSeconds,
                    interactionType = interactionType,
                    complete = true,
                    timestamp = Date(timestamp),
                    isSynced = false
                )
            )
        }
    }
}
