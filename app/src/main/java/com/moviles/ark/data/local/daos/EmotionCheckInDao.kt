package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moviles.ark.data.local.entities.CheckInEntity
import kotlinx.coroutines.flow.Flow

//consultas de la tabla mood_checkins
//las que devuelven Flow avisan solas cada vez que la tabla cambia; las suspend se llaman una vez
@Dao
interface EmotionCheckInDao {

    //guarda un check-in y devuelve el id que le puso room
    @Insert
    suspend fun insertCheckIn(checkIn: CheckInEntity): Long

    //todos los check-ins, del mas nuevo al mas viejo
    @Query("SELECT * FROM mood_checkins ORDER BY timestamp DESC")
    fun getCheckIns(): Flow<List<CheckInEntity>>

    //historial desde una fecha, por ejemplo la ultima semana para las estadisticas (#15)
    @Query("SELECT * FROM mood_checkins WHERE timestamp >= :fromMillis ORDER BY timestamp ASC")
    fun getCheckInsSince(fromMillis: Long): Flow<List<CheckInEntity>>

    //solo las fechas, para calcular la racha de dias seguidos (perfil #6 y estadisticas #15)
    @Query("SELECT timestamp FROM mood_checkins ORDER BY timestamp DESC")
    fun getCheckInTimestamps(): Flow<List<Long>>

    //los que faltan por subir a firestore (#33)
    @Query("SELECT * FROM mood_checkins WHERE isSynced = 0")
    suspend fun getUnsyncedCheckIns(): List<CheckInEntity>

    //despues de subirlos, se marcan como sincronizados
    @Query("UPDATE mood_checkins SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)
}
