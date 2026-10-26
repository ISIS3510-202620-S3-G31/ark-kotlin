package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.FeedbackModel
import kotlinx.coroutines.flow.Flow

// Repositorio de dominio para gestionar calificaciones y comentarios de herramientas (#166)
// Desacopla la persistencia local en Room SQLite de la sincronizacion remota en Firestore
interface FeedbackRepository {

    // Guarda una calificacion en Room SQLite y la sincroniza a Firestore (#21, #166)
    suspend fun saveFeedback(feedback: FeedbackModel): Result<FeedbackModel>

    // Obtiene las calificaciones de una herramienta especifica, de mas reciente a mas antigua
    fun getFeedbackForTool(toolId: String): Flow<List<FeedbackModel>>

    // Promedio de estrellas de una herramienta; null si todavia no ha recibido calificaciones
    fun getAverageRating(toolId: String): Flow<Double?>

    // Sincroniza calificaciones que aun esten pendientes (isSynced = false) hacia Firestore (#33)
    suspend fun syncPendingFeedback(): Result<Int>
}
