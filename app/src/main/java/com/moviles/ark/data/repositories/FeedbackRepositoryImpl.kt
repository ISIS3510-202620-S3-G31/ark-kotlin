package com.moviles.ark.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.entities.toDomain
import com.moviles.ark.data.local.entities.toEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.toDto
import com.moviles.ark.domain.models.FeedbackModel
import com.moviles.ark.domain.repositories.FeedbackRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// Implementacion del repositorio de feedback usando Room (FeedbackDao) y Firestore (FeedbackDto)
// Garantiza funcionamiento 100% offline y sincronizacion bidireccional con Firestore
class FeedbackRepositoryImpl(
    private val feedbackDao: FeedbackDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onFeedbackSaved: () -> Unit = {}
) : FeedbackRepository {

    override suspend fun saveFeedback(feedback: FeedbackModel): Result<FeedbackModel> = withContext(ioDispatcher) {
        runCatching {
            val currentUserId = auth.currentUser?.uid ?: "anonymous"

            // 1. Guardar primero en Room SQLite (offline-first) con isSynced = false
            val entityToInsert = feedback.toEntity().copy(isSynced = false)
            val insertedId = feedbackDao.insertFeedback(entityToInsert)
            val localSavedModel = feedback.copy(id = insertedId, isSynced = false)

            // 2. Intentar sincronizar inmediatamente a Firestore si hay conexion
            try {
                val dto = localSavedModel.toDto(currentUserId)
                firestore.collection("users")
                    .document(currentUserId)
                    .collection("tool_feedback")
                    .add(dto)
                    .await()

                feedbackDao.markAsSynced(listOf(insertedId))
                Log.d("FeedbackRepository", "Feedback id=$insertedId synced successfully to Firestore")
                localSavedModel.copy(isSynced = true)
            } catch (e: Exception) {
                Log.w("FeedbackRepository", "Could not sync feedback to Firestore immediately (offline or network error): ${e.message}")
                onFeedbackSaved()
                localSavedModel
            }
        }.onFailure { error ->
            Log.e("FeedbackRepository", "Error saving feedback", error)
            CrashlyticsHelper.logNonFatal(
                componentName = "FeedbackRepository",
                action = "saveFeedback",
                throwable = error,
                extraKeys = mapOf(
                    "toolId" to feedback.toolId,
                    "rating" to feedback.rating
                )
            )
        }
    }

    override fun getFeedbackForTool(toolId: String): Flow<List<FeedbackModel>> {
        return feedbackDao.getFeedbackForTool(toolId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAverageRating(toolId: String): Flow<Double?> {
        return feedbackDao.getAverageRating(toolId)
    }

    override suspend fun syncPendingFeedback(): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val currentUserId = auth.currentUser?.uid ?: "anonymous"
            val unsyncedFeedback = feedbackDao.getUnsyncedFeedback()
            var syncedCount = 0

            if (unsyncedFeedback.isNotEmpty()) {
                val successfulIds = mutableListOf<Long>()
                for (feedback in unsyncedFeedback) {
                    try {
                        val dto = feedback.toDto(currentUserId)
                        firestore.collection("users")
                            .document(currentUserId)
                            .collection("tool_feedback")
                            .add(dto)
                            .await()

                        successfulIds.add(feedback.id)
                        syncedCount++
                    } catch (e: Exception) {
                        Log.e("FeedbackRepository", "Failed to sync feedback id=${feedback.id}", e)
                    }
                }
                if (successfulIds.isNotEmpty()) {
                    feedbackDao.markAsSynced(successfulIds)
                }
            }
            syncedCount
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "FeedbackRepository",
                action = "syncPendingFeedback",
                throwable = error
            )
        }
    }
}
