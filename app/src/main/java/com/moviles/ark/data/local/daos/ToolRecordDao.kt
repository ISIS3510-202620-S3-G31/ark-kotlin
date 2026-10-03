package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moviles.ark.data.local.entities.ToolInteractionEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

//resultado de contar interacciones por herramienta; los nombres coinciden con las columnas del SELECT
data class ToolUsageCount(
    val toolId: String,
    val uses: Int,
)

//consultas de la tabla tool_interactions
@Dao
interface ToolRecordDao {

    //guarda una interaccion y devuelve su id
    @Insert
    suspend fun insertInteractionRaw(interaction: ToolInteractionEntity): Long

    suspend fun insertInteraction(interaction: ToolInteractionEntity): Long {
        return try {
            insertInteractionRaw(interaction)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolRecordDao",
                action = "insertInteraction",
                throwable = e,
                extraKeys = mapOf("toolId" to interaction.toolId, "timestamp" to interaction.timestamp)
            )
            throw e
        }
    }

    //todas las interacciones, de la mas nueva a la mas vieja
    @Query("SELECT * FROM tool_interactions ORDER BY timestamp DESC")
    fun getInteractionsRaw(): Flow<List<ToolInteractionEntity>>

    fun getInteractions(): Flow<List<ToolInteractionEntity>> {
        return getInteractionsRaw().catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolRecordDao",
                action = "getInteractions",
                throwable = e
            )
            throw e
        }
    }

    //interacciones desde una fecha en milisegundos (estadisticas e insights)
    @Query("SELECT * FROM tool_interactions WHERE timestamp >= :fromMillis ORDER BY timestamp ASC")
    fun getInteractionsSinceRaw(fromMillis: Long): Flow<List<ToolInteractionEntity>>

    fun getInteractionsSince(fromMillis: Long): Flow<List<ToolInteractionEntity>> {
        return getInteractionsSinceRaw(fromMillis).catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolRecordDao",
                action = "getInteractionsSince",
                throwable = e,
                extraKeys = mapOf("fromMillis" to fromMillis)
            )
            throw e
        }
    }

    //cuantas veces se uso cada herramienta, de la mas usada a la menos (#15 torta y #22 por frecuencia)
    @Query("SELECT toolId, COUNT(*) AS uses FROM tool_interactions GROUP BY toolId ORDER BY uses DESC")
    fun getUsageCountsRaw(): Flow<List<ToolUsageCount>>

    fun getUsageCounts(): Flow<List<ToolUsageCount>> {
        return getUsageCountsRaw().catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolRecordDao",
                action = "getUsageCounts",
                throwable = e
            )
            throw e
        }
    }

    //las que faltan por subir a firestore (#33)
    @Query("SELECT * FROM tool_interactions WHERE isSynced = 0")
    suspend fun getUnsyncedInteractionsRaw(): List<ToolInteractionEntity>

    suspend fun getUnsyncedInteractions(): List<ToolInteractionEntity> {
        return try {
            getUnsyncedInteractionsRaw()
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolRecordDao",
                action = "getUnsyncedInteractions",
                throwable = e
            )
            emptyList()
        }
    }

    @Query("UPDATE tool_interactions SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSyncedRaw(ids: List<Long>)

    suspend fun markAsSynced(ids: List<Long>) {
        try {
            markAsSyncedRaw(ids)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolRecordDao",
                action = "markAsSynced",
                throwable = e,
                extraKeys = mapOf("ids_count" to ids.size)
            )
            throw e
        }
    }
}
