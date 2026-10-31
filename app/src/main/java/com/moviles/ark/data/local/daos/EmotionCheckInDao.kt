package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moviles.ark.data.local.entities.CheckInEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.models.MoodCheckInModel
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

    //historial desde una fecha, por ejemplo la ultima semana para las estadisticas
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

    //solo las fechas, para calcular la racha de dias seguidos (perfil y estadisticas)
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

    //el ultimo check-in del usuario; el home lo lee de aqui para funcionar sin internet
    @Query("SELECT * FROM mood_checkins WHERE userId = :userId AND $DAILY_ONLY ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestCheckInRaw(userId: String): CheckInEntity?

    suspend fun getLatestCheckIn(userId: String): CheckInEntity? {
        return try {
            getLatestCheckInRaw(userId)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "EmotionCheckInDao",
                action = "getLatestCheckIn",
                throwable = e
            )
            null
        }
    }

    //cuantos check-ins tiene el usuario desde una fecha; con 0 desde el inicio del dia todavia no ha hecho el de hoy
    @Query("SELECT COUNT(*) FROM mood_checkins WHERE userId = :userId AND timestamp >= :fromMillis AND $DAILY_ONLY")
    suspend fun countCheckInsSince(userId: String, fromMillis: Long): Int

    //los que faltan por subir a firestore; solo los del usuario con sesion, porque se suben a su carpeta
    @Query("SELECT * FROM mood_checkins WHERE isSynced = 0 AND userId = :userId")
    suspend fun getUnsyncedCheckInsRaw(userId: String): List<CheckInEntity>

    suspend fun getUnsyncedCheckIns(userId: String): List<CheckInEntity> {
        return try {
            getUnsyncedCheckInsRaw(userId)
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
    //fechas de los check-ins diarios del usuario guardados en el telefono (incluye los que aun no se suben)
    @Query("SELECT timestamp FROM mood_checkins WHERE userId = :userId AND timestamp >= :fromMillis AND $DAILY_ONLY")
    suspend fun getDailyTimestampsSince(userId: String, fromMillis: Long): List<Long>

    //ciudad que llego despues (el check-in se guardo sin red); isSynced = 0 para que el sync la suba al mismo documento
    @Query("UPDATE mood_checkins SET cityName = :cityName, isSynced = 0 WHERE userId = :userId AND timestamp = :timestamp")
    suspend fun updateCityName(userId: String, timestamp: Long, cityName: String)

    //despues de subir un check-in: solo queda sincronizado si la ciudad no cambio mientras subia;
    //si llego la ciudad en ese momento, la fila sigue pendiente y el siguiente sync la sube
    @Query("UPDATE mood_checkins SET isSynced = 1 WHERE id = :id AND cityName IS :uploadedCityName")
    suspend fun markAsSyncedIfUnchanged(id: Long, uploadedCityName: String?)

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

    companion object {
        //los check-ins que guardaba el feedback viejo no son el check-in del dia: no bloquean, no cuentan
        const val DAILY_ONLY = "note NOT LIKE '${MoodCheckInModel.POST_TOOL_NOTE_PREFIX}%'"
    }
}
