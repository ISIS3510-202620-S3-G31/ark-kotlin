package com.moviles.ark.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.data.local.daos.EmotionCheckInDao
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.daos.PhotoEntryDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.repositories.SyncRepository
import kotlinx.coroutines.tasks.await

//implementacion de resincronizacion de datos offline hacia firebase (#33)
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
                        val dataMap = hashMapOf(
                            "userId" to currentUserId,
                            "toolId" to interaction.toolId,
                            "timestamp" to interaction.timestamp,
                            "duration" to interaction.duration,
                            "interactionType" to interaction.interactionType,
                            "intensity" to interaction.intensity,
                            "complete" to interaction.complete,
                            "value" to interaction.value,
                            "description" to interaction.description
                        )
                        firestore.collection("users")
                            .document(currentUserId)
                            .collection("tool_interactions")
                            .add(dataMap)
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
        try {
            val unsyncedPhotos = photoEntryDao.getUnsyncedPhotos()
            for (photo in unsyncedPhotos) {
                try {
                    val photoMap = hashMapOf(
                        "userId" to currentUserId,
                        "caption" to photo.caption,
                        "takenAt" to photo.takenAt,
                        "localFilePath" to photo.localFilePath
                    )
                    val docRef = firestore.collection("users")
                        .document(currentUserId)
                        .collection("photos")
                        .add(photoMap)
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
                        val emotionsData = checkIn.emotions.map { emotionName ->
                            mapOf(
                                "name" to emotionName,
                                "intensity" to checkIn.intensity
                            )
                        }
                        val checkInMap = hashMapOf(
                            "userId" to currentUserId,
                            "moodName" to checkIn.moodName,
                            "timestamp" to checkIn.timestamp,
                            "latitude" to checkIn.latitude,
                            "longitude" to checkIn.longitude,
                            "emotions" to emotionsData,
                            "note" to checkIn.note
                        )
                        firestore.collection("users")
                            .document(currentUserId)
                            .collection("mood_checkins")
                            .add(checkInMap)
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
                        val feedbackMap = hashMapOf(
                            "userId" to currentUserId,
                            "toolId" to feedback.toolId,
                            "rating" to feedback.rating,
                            "comment" to feedback.comment,
                            "createdAt" to feedback.createdAt,
                            "interactionId" to feedback.interactionId
                        )
                        firestore.collection("users")
                            .document(currentUserId)
                            .collection("tool_feedback")
                            .add(feedbackMap)
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
