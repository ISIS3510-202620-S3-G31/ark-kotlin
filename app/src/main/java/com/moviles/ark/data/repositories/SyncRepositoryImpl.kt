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
        val unsyncedInteractions = toolRecordDao.getUnsyncedInteractions()
        if (unsyncedInteractions.isNotEmpty()) {
            val successfulInteractionIds = mutableListOf<Long>()
            for (interaction in unsyncedInteractions) {
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
            }
            if (successfulInteractionIds.isNotEmpty()) {
                toolRecordDao.markAsSynced(successfulInteractionIds)
            }
        }

        // 2. Sincronizar fotos pendientes (isSynced = 0)
        val unsyncedPhotos = photoEntryDao.getUnsyncedPhotos()
        for (photo in unsyncedPhotos) {
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
        }

        // 3. Sincronizar check-ins de animo pendientes (isSynced = 0)
        val unsyncedCheckIns = emotionCheckInDao.getUnsyncedCheckIns()
        if (unsyncedCheckIns.isNotEmpty()) {
            val successfulCheckInIds = mutableListOf<Long>()
            for (checkIn in unsyncedCheckIns) {
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
            }
            if (successfulCheckInIds.isNotEmpty()) {
                emotionCheckInDao.markAsSynced(successfulCheckInIds)
            }
        }

        // 4. Sincronizar feedback de herramientas pendientes (isSynced = 0)
        val unsyncedFeedback = feedbackDao.getUnsyncedFeedback()
        if (unsyncedFeedback.isNotEmpty()) {
            val successfulFeedbackIds = mutableListOf<Long>()
            for (feedback in unsyncedFeedback) {
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
            }
            if (successfulFeedbackIds.isNotEmpty()) {
                feedbackDao.markAsSynced(successfulFeedbackIds)
            }
        }

        Log.d("SyncRepository", "Successfully synced $syncedCount pending items to Firebase")
        syncedCount
    }.onFailure { e ->
        Log.e("SyncRepository", "Failed to sync pending data to Firebase", e)
        CrashlyticsHelper.logNonFatal(
            componentName = "SyncRepository",
            action = "syncPendingData",
            throwable = e,
            extraKeys = mapOf("user_id" to (auth.currentUser?.uid ?: "anonymous"))
        )
    }
}
