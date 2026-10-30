package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moviles.ark.data.local.entities.FeedbackEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

//consultas de la tabla tool_feedback
@Dao
interface FeedbackDao {

    //guarda una calificacion y devuelve su id
    @Insert
    suspend fun insertFeedbackRaw(feedback: FeedbackEntity): Long

    suspend fun insertFeedback(feedback: FeedbackEntity): Long {
        return try {
            insertFeedbackRaw(feedback)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "FeedbackDao",
                action = "insertFeedback",
                throwable = e,
                extraKeys = mapOf("toolId" to feedback.toolId, "rating" to feedback.rating)
            )
            throw e
        }
    }

    //calificaciones de una herramienta, de la mas nueva a la mas vieja
    @Query("SELECT * FROM tool_feedback WHERE toolId = :toolId ORDER BY createdAt DESC")
    fun getFeedbackForToolRaw(toolId: String): Flow<List<FeedbackEntity>>

    fun getFeedbackForTool(toolId: String): Flow<List<FeedbackEntity>> {
        return getFeedbackForToolRaw(toolId).catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "FeedbackDao",
                action = "getFeedbackForTool",
                throwable = e,
                extraKeys = mapOf("toolId" to toolId)
            )
            throw e
        }
    }

    //promedio de estrellas de una herramienta; null si nadie la ha calificado
    @Query("SELECT AVG(rating) FROM tool_feedback WHERE toolId = :toolId")
    fun getAverageRatingRaw(toolId: String): Flow<Double?>

    fun getAverageRating(toolId: String): Flow<Double?> {
        return getAverageRatingRaw(toolId).catch { e ->
            CrashlyticsHelper.logNonFatal(
                componentName = "FeedbackDao",
                action = "getAverageRating",
                throwable = e,
                extraKeys = mapOf("toolId" to toolId)
            )
            throw e
        }
    }

    //las que faltan por subir a firestore
    //feedback pendiente de la cuenta abierta (y el de antes de guardar la cuenta)
    @Query("SELECT * FROM tool_feedback WHERE isSynced = 0 AND (userId = :userId OR userId = '')")
    suspend fun getUnsyncedFeedbackRaw(userId: String): List<FeedbackEntity>

    suspend fun getUnsyncedFeedback(userId: String): List<FeedbackEntity> {
        return try {
            getUnsyncedFeedbackRaw(userId)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "FeedbackDao",
                action = "getUnsyncedFeedback",
                throwable = e
            )
            emptyList()
        }
    }

    //las ya subidas a firestore se borran del telefono: alla queda la copia
    //si falla, el sync lo registra en crashlytics y las filas se vuelven a subir con el mismo id (sin duplicar)
    @Query("DELETE FROM tool_feedback WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    //filas que versiones anteriores marcaban como subidas en vez de borrarlas
    @Query("DELETE FROM tool_feedback WHERE isSynced = 1")
    suspend fun deleteAlreadySynced()
}

