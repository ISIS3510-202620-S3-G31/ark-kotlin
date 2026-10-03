package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moviles.ark.data.local.entities.FeedbackEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

//consultas de la tabla tool_feedback (#21)
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

    //las que faltan por subir a firestore (#33)
    @Query("SELECT * FROM tool_feedback WHERE isSynced = 0")
    suspend fun getUnsyncedFeedbackRaw(): List<FeedbackEntity>

    suspend fun getUnsyncedFeedback(): List<FeedbackEntity> {
        return try {
            getUnsyncedFeedbackRaw()
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "FeedbackDao",
                action = "getUnsyncedFeedback",
                throwable = e
            )
            emptyList()
        }
    }

    @Query("UPDATE tool_feedback SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSyncedRaw(ids: List<Long>)

    suspend fun markAsSynced(ids: List<Long>) {
        try {
            markAsSyncedRaw(ids)
        } catch (e: Exception) {
            CrashlyticsHelper.logNonFatal(
                componentName = "FeedbackDao",
                action = "markAsSynced",
                throwable = e,
                extraKeys = mapOf("ids_count" to ids.size)
            )
            throw e
        }
    }
}

