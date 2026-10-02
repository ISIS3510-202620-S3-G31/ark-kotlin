package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moviles.ark.data.local.entities.ToolSessionEntity
import kotlinx.coroutines.flow.Flow

//resultado de contar sesiones por herramienta; los nombres coinciden con las columnas del SELECT
data class ToolUsageCount(
    val toolId: String,
    val uses: Int,
)

//consultas de la tabla tool_sessions
@Dao
interface ToolRecordDao {

    //guarda una sesion terminada y devuelve su id
    @Insert
    suspend fun insertSession(session: ToolSessionEntity): Long

    //todas las sesiones, de la mas nueva a la mas vieja
    @Query("SELECT * FROM tool_sessions ORDER BY startedAt DESC")
    fun getSessions(): Flow<List<ToolSessionEntity>>

    //sesiones desde una fecha (estadisticas e insights)
    @Query("SELECT * FROM tool_sessions WHERE startedAt >= :fromMillis ORDER BY startedAt ASC")
    fun getSessionsSince(fromMillis: Long): Flow<List<ToolSessionEntity>>

    //cuantas veces se uso cada herramienta, de la mas usada a la menos (#15 torta y #22 por frecuencia)
    @Query("SELECT toolId, COUNT(*) AS uses FROM tool_sessions GROUP BY toolId ORDER BY uses DESC")
    fun getUsageCounts(): Flow<List<ToolUsageCount>>

    //las que faltan por subir a firestore (#33)
    @Query("SELECT * FROM tool_sessions WHERE isSynced = 0")
    suspend fun getUnsyncedSessions(): List<ToolSessionEntity>

    @Query("UPDATE tool_sessions SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)
}
