package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moviles.ark.data.local.entities.CheckInEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

//consultas de la tabla mood_checkins
//las que devuelven Flow avisan solas cada vez que la tabla cambia; las suspend se llaman una vez
@Dao
interface EmotionCheckInDao {

    //guarda un check-in y devuelve el id que le puso room
    @Insert
    suspend fun insertCheckInRaw(checkIn: CheckInEntity): Long

    suspend fun insertCheckIn(checkIn: CheckInEntity): Long {
        return try {
            insertCheckInRaw(checkIn)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "EmotionCheckInDao",
                action = "insertCheckIn",
                throwable = e,
                extraKeys = mapOf("timestamp" to checkIn.timestamp)
            )
            throw e
        }
    }

    //todos los check-ins, del mas nuevo al mas viejo
    @Query("SELECT * FROM mood_checkins ORDER BY timestamp DESC")
    fun getCheckInsRaw(): Flow<List<CheckInEntity>>

    fun getCheckIns(): Flow<List<CheckInEntity>> {
        return getCheckInsRaw().catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "EmotionCheckInDao",
                action = "getCheckIns",
                throwable = e
            )
            throw e
        }
    }

    //historial desde una fecha, por ejemplo la ultima semana para las estadisticas (#15)
    @Query("SELECT * FROM mood_checkins WHERE timestamp >= :fromMillis ORDER BY timestamp ASC")
    fun getCheckInsSinceRaw(fromMillis: Long): Flow<List<CheckInEntity>>

    fun getCheckInsSince(fromMillis: Long): Flow<List<CheckInEntity>> {
        return getCheckInsSinceRaw(fromMillis).catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "EmotionCheckInDao",
                action = "getCheckInsSince",
                throwable = e,
                extraKeys = mapOf("fromMillis" to fromMillis)
            )
            throw e
        }
    }

    //solo las fechas, para calcular la racha de dias seguidos (perfil #6 y estadisticas #15)
    @Query("SELECT timestamp FROM mood_checkins ORDER BY timestamp DESC")
    fun getCheckInTimestampsRaw(): Flow<List<Long>>

    fun getCheckInTimestamps(): Flow<List<Long>> {
        return getCheckInTimestampsRaw().catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "EmotionCheckInDao",
                action = "getCheckInTimestamps",
                throwable = e
            )
            throw e
        }
    }

    //los que faltan por subir a firestore (#33)
    @Query("SELECT * FROM mood_checkins WHERE isSynced = 0")
    suspend fun getUnsyncedCheckInsRaw(): List<CheckInEntity>

    suspend fun getUnsyncedCheckIns(): List<CheckInEntity> {
        return try {
            getUnsyncedCheckInsRaw()
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "EmotionCheckInDao",
                action = "getUnsyncedCheckIns",
                throwable = e
            )
            emptyList()
        }
    }

    //despues de subirlos, se marcan como sincronizados
    @Query("UPDATE mood_checkins SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSyncedRaw(ids: List<Long>)

    suspend fun markAsSynced(ids: List<Long>) {
        try {
            markAsSyncedRaw(ids)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "EmotionCheckInDao",
                action = "markAsSynced",
                throwable = e,
                extraKeys = mapOf("ids_count" to ids.size)
            )
            throw e
        }
    }
}

