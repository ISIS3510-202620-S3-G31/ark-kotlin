package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moviles.ark.data.local.entities.ToolInteractionEntity
import kotlinx.coroutines.flow.Flow

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
    suspend fun insertInteraction(interaction: ToolInteractionEntity): Long

    //todas las interacciones, de la mas nueva a la mas vieja
    @Query("SELECT * FROM tool_interactions ORDER BY startedAt DESC")
    fun getInteractions(): Flow<List<ToolInteractionEntity>>

    //interacciones desde una fecha (estadisticas e insights)
    @Query("SELECT * FROM tool_interactions WHERE startedAt >= :fromMillis ORDER BY startedAt ASC")
    fun getInteractionsSince(fromMillis: Long): Flow<List<ToolInteractionEntity>>

    //cuantas veces se uso cada herramienta, de la mas usada a la menos (#15 torta y #22 por frecuencia)
    @Query("SELECT toolId, COUNT(*) AS uses FROM tool_interactions GROUP BY toolId ORDER BY uses DESC")
    fun getUsageCounts(): Flow<List<ToolUsageCount>>

    //las que faltan por subir a firestore (#33)
    @Query("SELECT * FROM tool_interactions WHERE isSynced = 0")
    suspend fun getUnsyncedInteractions(): List<ToolInteractionEntity>

    @Query("UPDATE tool_interactions SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)
}
