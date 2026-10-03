package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moviles.ark.data.local.entities.ToolSessionEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

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
    suspend fun insertSessionRaw(session: ToolSessionEntity): Long

    suspend fun insertSession(session: ToolSessionEntity): Long {
        return try {
            insertSessionRaw(session)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolRecordDao",
                action = "insertSession",
                throwable = e,
                extraKeys = mapOf("toolId" to session.toolId, "startedAt" to session.startedAt)
            )
            throw e
        }
    }

    //todas las sesiones, de la mas nueva a la mas vieja
    @Query("SELECT * FROM tool_sessions ORDER BY startedAt DESC")
    fun getSessionsRaw(): Flow<List<ToolSessionEntity>>

    fun getSessions(): Flow<List<ToolSessionEntity>> {
        return getSessionsRaw().catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolRecordDao",
                action = "getSessions",
                throwable = e
            )
            throw e
        }
    }

    //sesiones desde una fecha (estadisticas e insights)
    @Query("SELECT * FROM tool_sessions WHERE startedAt >= :fromMillis ORDER BY startedAt ASC")
    fun getSessionsSinceRaw(fromMillis: Long): Flow<List<ToolSessionEntity>>

    fun getSessionsSince(fromMillis: Long): Flow<List<ToolSessionEntity>> {
        return getSessionsSinceRaw(fromMillis).catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolRecordDao",
                action = "getSessionsSince",
                throwable = e,
                extraKeys = mapOf("fromMillis" to fromMillis)
            )
            throw e
        }
    }

    //cuantas veces se uso cada herramienta, de la mas usada a la menos (#15 torta y #22 por frecuencia)
    @Query("SELECT toolId, COUNT(*) AS uses FROM tool_sessions GROUP BY toolId ORDER BY uses DESC")
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
    @Query("SELECT * FROM tool_sessions WHERE isSynced = 0")
    suspend fun getUnsyncedSessionsRaw(): List<ToolSessionEntity>

    suspend fun getUnsyncedSessions(): List<ToolSessionEntity> {
        return try {
            getUnsyncedSessionsRaw()
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolRecordDao",
                action = "getUnsyncedSessions",
                throwable = e
            )
            emptyList()
        }
    }

    @Query("UPDATE tool_sessions SET isSynced = 1 WHERE id IN (:ids)")
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

