package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moviles.ark.data.local.entities.BreathingSessionEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

//consultas de la tabla breathing_sessions (#82)
@Dao
interface BreathingSessionDao {

    //guarda una sesion terminada y devuelve su id
    @Insert
    suspend fun insertSessionRaw(session: BreathingSessionEntity): Long

    suspend fun insertSession(session: BreathingSessionEntity): Long {
        return try {
            insertSessionRaw(session)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "BreathingSessionDao",
                action = "insertSession",
                throwable = e,
                extraKeys = mapOf("completedCycles" to session.completedCycles, "timestamp" to session.timestamp)
            )
            throw e
        }
    }

    //todas las sesiones, de la mas nueva a la mas vieja
    @Query("SELECT * FROM breathing_sessions ORDER BY timestamp DESC")
    fun getSessionsRaw(): Flow<List<BreathingSessionEntity>>

    fun getSessions(): Flow<List<BreathingSessionEntity>> {
        return getSessionsRaw().catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "BreathingSessionDao",
                action = "getSessions",
                throwable = e
            )
            throw e
        }
    }
}
