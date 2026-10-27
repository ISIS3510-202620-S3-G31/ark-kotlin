package com.moviles.ark.data.repositories

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.moviles.ark.data.local.daos.EmotionCheckInDao
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.daos.PhotoEntryDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.PhotoEntryEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.toDto
import com.moviles.ark.domain.repositories.SyncRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.InputStream

//implementacion de resincronizacion de datos offline hacia firebase
//cada fila de room se pasa a su dto antes de subirla: firestore recibe siempre los mismos campos y tipos
class SyncRepositoryImpl(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val photoEntryDao: PhotoEntryDao,
    private val toolRecordDao: ToolRecordDao,
    private val emotionCheckInDao: EmotionCheckInDao,
    private val feedbackDao: FeedbackDao
) : SyncRepository {

    //una sola subida a la vez: el check-in pide subida al guardarse y el SyncManager al volver la red
    private val syncMutex = Mutex()

    override suspend fun syncPendingData(): Result<Int> = syncMutex.withLock { syncAllPending() }

    //sube el jpg de la foto a storage y devuelve su url de descarga
    //null si el archivo ya no existe (lo borraron de la galeria); si falla la red lanza y la foto se reintenta despues
    private suspend fun uploadPhotoFile(photo: PhotoEntryEntity): String? {
        val input = openPhoto(photo.localFilePath) ?: return null
        val reference = storage.reference.child(PhotoEntryRepositoryImpl.storagePath(photo.userId, photo.takenAt))
        input.use { reference.putStream(it).await() }
        return reference.downloadUrl.await().toString()
    }

    private fun openPhoto(path: String): InputStream? = runCatching {
        when {
            path.isBlank() -> null
            path.startsWith("content://") || path.startsWith("file://") -> context.contentResolver.openInputStream(Uri.parse(path))
            else -> File(path).takeIf { it.exists() }?.inputStream()
        }
    }.getOrNull()

    private suspend fun syncAllPending(): Result<Int> = runCatching {
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
        //solo las del usuario con sesion: las reglas de firestore no dejan escribir en la carpeta de otro;
        //las de otra cuenta esperan a que esa cuenta vuelva a entrar
        try {
            val sessionUserId = auth.currentUser?.uid
            val unsyncedPhotos = if (sessionUserId != null) photoEntryDao.getUnsyncedPhotos(sessionUserId) else emptyList()
            for (photo in unsyncedPhotos) {
                try {
                    //primero el jpg a storage; si el archivo ya no esta en el telefono se sube solo el registro
                    val downloadUrl = uploadPhotoFile(photo)
                    //cada foto va a users/{su userId}/photos/{takenAt}: si se sube dos veces queda un solo documento
                    firestore.collection("users")
                        .document(photo.userId)
                        .collection("photos")
                        .document(photo.takenAt.toString())
                        .set(photo.toDto().copy(remoteUrl = downloadUrl.orEmpty()))
                        .await()

                    photoEntryDao.markAsSynced(photo.id, remoteUrl = downloadUrl.orEmpty())
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
            val unsyncedCheckIns = emotionCheckInDao.getUnsyncedCheckIns(currentUserId)
            if (unsyncedCheckIns.isNotEmpty()) {
                val successfulCheckInIds = mutableListOf<Long>()
                for (checkIn in unsyncedCheckIns) {
                    try {
                        //documento con el remoteId de la fila: si se sube dos veces queda uno solo
                        firestore.collection("users")
                            .document(currentUserId)
                            .collection("mood_checkins")
                            .document(checkIn.remoteId)
                            .set(checkIn.toDto(currentUserId))
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
