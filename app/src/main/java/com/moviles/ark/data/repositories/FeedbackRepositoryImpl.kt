package com.moviles.ark.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.entities.toDomain
import com.moviles.ark.data.local.entities.toEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.documentId
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
// Room guarda el feedback mientras no hay internet; al subirlo a Firestore se borra del telefono
class FeedbackRepositoryImpl(
    private val feedbackDao: FeedbackDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onFeedbackSaved: () -> Unit = {}
) : FeedbackRepository {

    //guarda solo en room (isSynced = false) y avisa al SyncManager; no espera a firestore
    //sin internet esa espera no terminaba nunca y la pantalla de feedback se quedaba cargando
    override suspend fun saveFeedback(feedback: FeedbackModel): Result<FeedbackModel> = withContext(ioDispatcher) {
        runCatching {
            val userId = auth.currentUser?.uid.orEmpty()
            val insertedId = feedbackDao.insertFeedback(feedback.toEntity().copy(userId = userId, isSynced = false))
            onFeedbackSaved()
            feedback.copy(id = insertedId, isSynced = false)
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

    //sube el feedback pendiente con el mismo id de documento que el sync y lo borra del telefono
    override suspend fun syncPendingFeedback(): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val currentUserId = auth.currentUser?.uid ?: return@runCatching 0
            val uploadedIds = mutableListOf<Long>()
            for (feedback in feedbackDao.getUnsyncedFeedback(currentUserId)) {
                try {
                    firestore.collection("users")
                        .document(currentUserId)
                        .collection("tool_feedback")
                        .document(feedback.documentId())
                        .set(feedback.toDto(currentUserId))
                        .await()
                    uploadedIds.add(feedback.id)
                } catch (e: Exception) {
                    Log.e("FeedbackRepository", "Failed to sync feedback id=${feedback.id}", e)
                }
            }
            if (uploadedIds.isNotEmpty()) {
                feedbackDao.deleteByIds(uploadedIds)
            }
            uploadedIds.size
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "FeedbackRepository",
                action = "syncPendingFeedback",
                throwable = error
            )
        }
    }
}
