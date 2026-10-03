package com.moviles.ark.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moviles.ark.data.local.entities.FeedbackEntity
import kotlinx.coroutines.flow.Flow

//consultas de la tabla tool_feedback (#21)
@Dao
interface FeedbackDao {

    //guarda una calificacion y devuelve su id
    @Insert
    suspend fun insertFeedback(feedback: FeedbackEntity): Long

    //calificaciones de una herramienta, de la mas nueva a la mas vieja
    @Query("SELECT * FROM tool_feedback WHERE toolId = :toolId ORDER BY createdAt DESC")
    fun getFeedbackForTool(toolId: String): Flow<List<FeedbackEntity>>

    //promedio de estrellas de una herramienta; null si nadie la ha calificado
    @Query("SELECT AVG(rating) FROM tool_feedback WHERE toolId = :toolId")
    fun getAverageRating(toolId: String): Flow<Double?>

    //las que faltan por subir a firestore (#33)
    @Query("SELECT * FROM tool_feedback WHERE isSynced = 0")
    suspend fun getUnsyncedFeedback(): List<FeedbackEntity>

    @Query("UPDATE tool_feedback SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)
}
