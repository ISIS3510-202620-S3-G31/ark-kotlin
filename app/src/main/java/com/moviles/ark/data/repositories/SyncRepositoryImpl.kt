package com.moviles.ark.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.data.local.daos.EmotionCheckInDao
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.daos.PhotoEntryDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.toDto
import com.moviles.ark.domain.repositories.SyncRepository
import kotlinx.coroutines.tasks.await

//implementacion de resincronizacion de datos offline hacia firebase (#33)
//cada fila de room se pasa a su dto antes de subirla (#163): firestore recibe siempre los mismos campos y tipos
class SyncRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val photoEntryDao: PhotoEntryDao,
    private val toolRecordDao: ToolRecordDao,
    private val emotionCheckInDao: EmotionCheckInDao,
    private val feedbackDao: FeedbackDao
) : SyncRepository {

    override suspend fun syncPendingData(): Result<Int> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: "anonymous"
        var syncedCount = 0

        // 1. Sincronizar interacciones de herramientas pendientes (isSynced = 0)
        try {
            val unsyncedInteractions = toolRecordDao.getUnsyncedInteractions()
            if (unsyncedInteractions.isNotEmpty()) {
                val successfulInteractionIds = mutableListOf<Long>()
                for (interaction in unsyncedInteractions) {
                    try {
                        firestore.collection("users")
                            .document(currentUserId)
                            .collection("tool_interactions")
                            .add(interaction.toDto(currentUserId))
                            .await()

                        successfulInteractionIds.add(interaction.id)
                        syncedCount++
                    } catch (e: Exception) {
                        Log.e("SyncRepository", "Failed to sync single tool interaction id=${interaction.id}", e)
                    }
                }
                if (successfulInteractionIds.isNotEmpty()) {
                    toolRecordDao.markAsSynced(successfulInteractionIds)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncRepository", "Error syncing tool interactions collection", e)
            CrashlyticsHelper.logNonFatal(
                componentName = "SyncRepository",
                action = "syncToolInteractions",
                throwable = e,
                extraKeys = mapOf("user_id" to currentUserId)
            )
        }

        // 2. Sincronizar fotos pendientes (isSynced = 0)
        //solo las del usuario con sesion (#133): las reglas de firestore no dejan escribir en la carpeta de otro;
        //las de otra cuenta esperan a que esa cuenta vuelva a entrar
        try {
            val sessionUserId = auth.currentUser?.uid
            val unsyncedPhotos = if (sessionUserId != null) photoEntryDao.getUnsyncedPhotos(sessionUserId) else emptyList()
            for (photo in unsyncedPhotos) {
                try {
                    val docRef = firestore.collection("users")
                        .document(photo.userId)
                        .collection("photos")
                        .add(photo.toDto(currentUserId))
                        .await()

                    photoEntryDao.markAsSynced(photo.id, remoteUrl = docRef.id)
                    syncedCount++
                } catch (e: Exception) {
                    Log.e("SyncRepository", "Failed to sync single photo id=${photo.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncRepository", "Error syncing photos collection", e)
            CrashlyticsHelper.logNonFatal(
                componentName = "SyncRepository",
                action = "syncPhotos",
                throwable = e,
                extraKeys = mapOf("user_id" to currentUserId)
            )
        }

        // 3. Sincronizar check-ins de animo pendientes (isSynced = 0)
        try {
            val unsyncedCheckIns = emotionCheckInDao.getUnsyncedCheckIns()
            if (unsyncedCheckIns.isNotEmpty()) {
                val successfulCheckInIds = mutableListOf<Long>()
                for (checkIn in unsyncedCheckIns) {
                    try {
                        firestore.collection("users")
                            .document(currentUserId)
                            .collection("mood_checkins")
                            .add(checkIn.toDto(currentUserId))
                            .await()

                        successfulCheckInIds.add(checkIn.id)
                        syncedCount++
                    } catch (e: Exception) {
                        Log.e("SyncRepository", "Failed to sync single check-in id=${checkIn.id}", e)
                    }
                }
                if (successfulCheckInIds.isNotEmpty()) {
                    emotionCheckInDao.markAsSynced(successfulCheckInIds)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncRepository", "Error syncing mood checkins collection", e)
            CrashlyticsHelper.logNonFatal(
                componentName = "SyncRepository",
                action = "syncMoodCheckIns",
                throwable = e,
                extraKeys = mapOf("user_id" to currentUserId)
            )
        }

        // 4. Sincronizar feedback de herramientas pendientes (isSynced = 0)
        try {
            val unsyncedFeedback = feedbackDao.getUnsyncedFeedback()
            if (unsyncedFeedback.isNotEmpty()) {
                val successfulFeedbackIds = mutableListOf<Long>()
                for (feedback in unsyncedFeedback) {
                    try {
                        firestore.collection("users")
                            .document(currentUserId)
                            .collection("tool_feedback")
                            .add(feedback.toDto(currentUserId))
                            .await()

                        successfulFeedbackIds.add(feedback.id)
                        syncedCount++
                    } catch (e: Exception) {
                        Log.e("SyncRepository", "Failed to sync single tool feedback id=${feedback.id}", e)
                    }
                }
                if (successfulFeedbackIds.isNotEmpty()) {
                    feedbackDao.markAsSynced(successfulFeedbackIds)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncRepository", "Error syncing feedback collection", e)
            CrashlyticsHelper.logNonFatal(
                componentName = "SyncRepository",
                action = "syncFeedback",
                throwable = e,
                extraKeys = mapOf("user_id" to currentUserId)
            )
        }

        Log.d("SyncRepository", "Successfully synced $syncedCount pending items to Firebase")
        syncedCount
    }
}
