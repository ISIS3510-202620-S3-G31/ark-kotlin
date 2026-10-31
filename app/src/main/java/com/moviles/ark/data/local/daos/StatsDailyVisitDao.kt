package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moviles.ark.data.local.entities.StatsDailyVisitEntity
import com.moviles.ark.data.remote.CrashlyticsHelper

//consultas para conteo diario de visitas a stats y sincronizacion offline
@Dao
interface StatsDailyVisitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRaw(visit: StatsDailyVisitEntity)

    suspend fun insertOrUpdate(visit: StatsDailyVisitEntity) {
        try {
            insertOrUpdateRaw(visit)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "StatsDailyVisitDao",
                action = "insertOrUpdate",
                throwable = e,
                extraKeys = mapOf("userId" to visit.userId, "dateKey" to visit.dateKey)
            )
            throw e
        }
    }

    //obtiene el registro de visitas para un usuario y dia especifico
    @Query("SELECT * FROM stats_daily_visits WHERE (userId = :userId OR userId = 'anonymous') AND dateKey = :dateKey LIMIT 1")
    suspend fun getVisitForDate(userId: String, dateKey: String): StatsDailyVisitEntity?

    //obtiene solo el numero de visitas del dia
    @Query("SELECT count FROM stats_daily_visits WHERE (userId = :userId OR userId = 'anonymous') AND dateKey = :dateKey LIMIT 1")
    suspend fun getCountForDate(userId: String, dateKey: String): Int?

    //visitas diarias que aun no se han sincronizado con Firestore
    @Query("SELECT * FROM stats_daily_visits WHERE isSynced = 0 AND (userId = :userId OR userId = 'anonymous')")
    suspend fun getUnsyncedVisits(userId: String): List<StatsDailyVisitEntity>

    //marca un dia como sincronizado, solo si el conteo subido sigue siendo el de la fila
    //(si el usuario volvio a stats mientras subia, el dia queda pendiente con el numero nuevo)
    @Query("UPDATE stats_daily_visits SET isSynced = 1 WHERE userId = :userId AND dateKey = :dateKey AND count = :uploadedCount")
    suspend fun markAsSyncedIfUnchanged(userId: String, dateKey: String, uploadedCount: Int)
}
