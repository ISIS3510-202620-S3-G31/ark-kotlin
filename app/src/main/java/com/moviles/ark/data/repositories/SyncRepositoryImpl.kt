package com.moviles.ark.data.repositories

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.moviles.ark.data.local.daos.EmotionCheckInDao
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.daos.PhotoEntryDao
import com.moviles.ark.data.local.daos.StatsDailyVisitDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.PhotoEntryEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.documentId
import com.moviles.ark.data.remote.dtos.toDto
import com.moviles.ark.data.workers.RemotePhotoDeleteWorker
import com.moviles.ark.domain.models.ProfileSyncResult
import com.moviles.ark.domain.repositories.SyncRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
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
    private val feedbackDao: FeedbackDao,
    private val statsDailyVisitDao: StatsDailyVisitDao? = null,
    //sube el perfil y la foto de perfil que se cambiaron sin internet (UserRepository.syncPendingProfile)
    private val syncPendingProfile: suspend () -> ProfileSyncResult = { ProfileSyncResult() },
    //true si el perfil o la foto de perfil tienen cambios sin subir (UserRepository.hasPendingProfileChanges)
    private val hasPendingProfile: suspend () -> Boolean = { false }
) : SyncRepository {

    //una sola subida a la vez: el check-in pide subida al guardarse y el SyncManager al volver la red
    private val syncMutex = Mutex()

    override suspend fun syncPendingData(): Result<Int> = syncMutex.withLock { syncAllPending() }

    //algo no se pudo subir (sin red, tiempo agotado); lo pendiente sigue en room y el SyncWorker lo reintenta
    class PendingUploadsException(val synced: Int, val failed: Int) :
        Exception("$failed pending items could not be uploaded ($synced uploaded)")

    //sube el jpg de la foto a storage y devuelve su url de descarga
    //null si el archivo ya no existe (lo borraron de la galeria); si falla la red lanza y la foto se reintenta despues
    private suspend fun uploadPhotoFile(photo: PhotoEntryEntity): String? {
        val input = openPhoto(photo.localFilePath) ?: return null
        val reference = storage.reference.child(PhotoEntryRepositoryImpl.storagePath(photo.userId, photo.takenAt))
        input.use { reference.putStream(it).awaitWithTimeout(PHOTO_UPLOAD_TIMEOUT_MS) }
        return reference.downloadUrl.awaitWithTimeout().toString()
    }

    //sin respuesta del servidor (red caida a mitad de la subida) se deja la fila pendiente para el proximo intento
    //en vez de esperar para siempre con el mutex tomado; firestore igual conserva la escritura en su cola
    private suspend fun <T> Task<T>.awaitWithTimeout(timeoutMs: Long = REMOTE_TIMEOUT_MS): T = withTimeout(timeoutMs) { await() }

    private fun openPhoto(path: String): InputStream? = runCatching {
        when {
            path.isBlank() -> null
            path.startsWith("content://") || path.startsWith("file://") -> context.contentResolver.openInputStream(Uri.parse(path))
            else -> File(path).takeIf { it.exists() }?.inputStream()
        }
    }.getOrNull()

    override suspend fun pendingCount(): Int {
        val userId = auth.currentUser?.uid ?: return 0
        return toolRecordDao.getUnsyncedInteractions().count { it.userId == userId || it.userId == ANONYMOUS_USER_ID } +
            photoEntryDao.getUnsyncedPhotos(userId).size +
            emotionCheckInDao.getUnsyncedCheckIns(userId).size +
            feedbackDao.getUnsyncedFeedback(userId).size +
            (statsDailyVisitDao?.getUnsyncedVisits(userId)?.size ?: 0) +
            (if (hasPendingProfile()) 1 else 0)
    }

    //cuantas filas se subieron y cuantas fallaron; con fallos el sync devuelve error y el SyncWorker reintenta con espera
    //(antes cada fallo se tragaba, el trabajo terminaba "bien" y lo pendiente esperaba hasta 15 minutos)
    private class SyncTally {
        var synced = 0
        var failed = 0
    }

    //intenta subir una fila; si falla se cuenta y se sigue con las demas
    //la cancelacion del sync (salir de stats, la red que va y viene) no se cuenta como fallo: se deja pasar
    private inline fun SyncTally.upload(label: String, block: () -> Unit) {
        try {
            block()
            synced++
        } catch (e: TimeoutCancellationException) {
            failed++
            Log.w("SyncRepository", "Timed out syncing $label", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed++
            Log.e("SyncRepository", "Failed to sync $label", e)
        }
    }

    //un error de toda una coleccion (por ejemplo leer room) se registra y el sync sigue con la siguiente
    private inline fun SyncTally.step(action: String, userId: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed++
            Log.e("SyncRepository", "Error in $action", e)
            CrashlyticsHelper.logNonFatal(
                componentName = "SyncRepository",
                action = action,
                throwable = e,
                extraKeys = mapOf("user_id" to userId)
            )
        }
    }

    //el orden importa: primero lo pequeno (interacciones, check-ins, feedback, visitas, perfil) y al final las fotos,
    //que pesan mas; asi stats (que espera el sync maximo 10 s) ya tiene los check-ins aunque una foto siga subiendo
    private suspend fun syncAllPending(): Result<Int> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: "anonymous"
        val tally = SyncTally()

        // 1. Subir las interacciones pendientes y borrarlas del telefono; firestore queda con la copia
        //solo las del usuario con sesion (o las guardadas sin sesion): las de otra cuenta esperan a que esa cuenta entre
        tally.step("syncToolInteractions", currentUserId) {
            toolRecordDao.deleteAlreadySynced()
            val sessionUserId = auth.currentUser?.uid
            val unsyncedInteractions = if (sessionUserId != null) {
                toolRecordDao.getUnsyncedInteractions().filter { it.userId == sessionUserId || it.userId == ANONYMOUS_USER_ID }
            } else {
                emptyList()
            }
            val uploadedInteractionIds = mutableListOf<Long>()
            for (interaction in unsyncedInteractions) {
                tally.upload("tool interaction id=${interaction.id}") {
                    //documento con id fijo: si la subida se corta y se repite queda uno solo
                    firestore.collection("users")
                        .document(currentUserId)
                        .collection("tool_interactions")
                        .document(interaction.documentId())
                        .set(interaction.toDto(currentUserId))
                        .awaitWithTimeout()
                    uploadedInteractionIds.add(interaction.id)
                }
            }
            if (uploadedInteractionIds.isNotEmpty()) {
                toolRecordDao.deleteByIds(uploadedInteractionIds)
            }
        }

        // 2. Sincronizar check-ins de animo pendientes (isSynced = 0)
        tally.step("syncMoodCheckIns", currentUserId) {
            for (checkIn in emotionCheckInDao.getUnsyncedCheckIns(currentUserId)) {
                tally.upload("check-in id=${checkIn.id}") {
                    //documento con el remoteId de la fila: si se sube dos veces queda uno solo
                    firestore.collection("users")
                        .document(currentUserId)
                        .collection("mood_checkins")
                        .document(checkIn.remoteId)
                        .set(checkIn.toDto(currentUserId))
                        .awaitWithTimeout()
                    //si mientras subia llego la ciudad (updateCityName), la fila queda pendiente para subirla tambien
                    emotionCheckInDao.markAsSyncedIfUnchanged(checkIn.id, checkIn.cityName)
                }
            }
        }

        // 3. Subir el feedback pendiente y borrarlo del telefono; firestore queda con la copia
        tally.step("syncFeedback", currentUserId) {
            feedbackDao.deleteAlreadySynced()
            val unsyncedFeedback = if (auth.currentUser != null) feedbackDao.getUnsyncedFeedback(currentUserId) else emptyList()
            val uploadedFeedbackIds = mutableListOf<Long>()
            for (feedback in unsyncedFeedback) {
                tally.upload("tool feedback id=${feedback.id}") {
                    firestore.collection("users")
                        .document(currentUserId)
                        .collection("tool_feedback")
                        .document(feedback.documentId())
                        .set(feedback.toDto(currentUserId))
                        .awaitWithTimeout()
                    uploadedFeedbackIds.add(feedback.id)
                }
            }
            if (uploadedFeedbackIds.isNotEmpty()) {
                feedbackDao.deleteByIds(uploadedFeedbackIds)
            }
        }

        // 4. Sincronizar visitas diarias a estadisticas pendientes (isSynced = 0)
        tally.step("syncDailyStatsVisits", currentUserId) {
            val visitDao = statsDailyVisitDao ?: return@step
            for (visit in visitDao.getUnsyncedVisits(currentUserId)) {
                tally.upload("daily stats visit dateKey=${visit.dateKey}") {
                    firestore.collection("users")
                        .document(currentUserId)
                        .collection("stats_daily_visits")
                        .document(visit.dateKey)
                        .set(
                            mapOf(
                                "userId" to currentUserId,
                                "dateKey" to visit.dateKey,
                                "count" to visit.count,
                                "lastUpdatedAt" to visit.lastUpdatedAt,
                                //en bigquery el emulador se separa de los celulares reales por el modelo
                                "deviceModel" to Build.MODEL
                            )
                        )
                        .awaitWithTimeout()
                    //con el userId de la fila: una visita guardada sin sesion ("anonymous") tambien queda marcada
                    //y solo si el conteo no cambio mientras subia; si cambio, queda pendiente con el numero nuevo
                    visitDao.markAsSyncedIfUnchanged(visit.userId, visit.dateKey, visit.count)
                }
            }
        }

        // 5. Sincronizar el perfil editado sin internet: nombre y fecha (users.isSynced = 0)
        //y el avatar o la foto de perfil (users.isAvatarSynced = 0); lo sube el repositorio del usuario
        tally.step("syncProfile", currentUserId) {
            val result = syncPendingProfile()
            tally.synced += result.synced
            tally.failed += result.failed
        }

        // 6. Sincronizar fotos pendientes (isSynced = 0)
        //solo las del usuario con sesion: las reglas de firestore no dejan escribir en la carpeta de otro;
        //las de otra cuenta esperan a que esa cuenta vuelva a entrar
        tally.step("syncPhotos", currentUserId) {
            val sessionUserId = auth.currentUser?.uid
            val unsyncedPhotos = if (sessionUserId != null) photoEntryDao.getUnsyncedPhotos(sessionUserId) else emptyList()
            for (photo in unsyncedPhotos) {
                tally.upload("photo id=${photo.id}") {
                    //primero el jpg a storage; si el archivo ya no esta en el telefono se sube solo el registro
                    val downloadUrl = uploadPhotoFile(photo)
                    //cada foto va a users/{su userId}/photos/{takenAt}: si se sube dos veces queda un solo documento
                    val document = firestore.collection("users")
                        .document(photo.userId)
                        .collection("photos")
                        .document(photo.takenAt.toString())
                    //el usuario pudo borrar la foto mientras subia (la subida del jpg tarda): no se crea su documento,
                    //o si ya se creo se borra; si no, al otro dia la nube la devolvia como foto de un dia pasado
                    if (photoEntryDao.getPhotoById(photo.id, photo.userId) == null) {
                        if (downloadUrl != null) RemotePhotoDeleteWorker.enqueue(context, photo.userId, photo.takenAt)
                        return@upload
                    }
                    document.set(photo.toDto().copy(remoteUrl = downloadUrl.orEmpty())).awaitWithTimeout()
                    if (photoEntryDao.getPhotoById(photo.id, photo.userId) == null) {
                        RemotePhotoDeleteWorker.enqueue(context, photo.userId, photo.takenAt)
                        return@upload
                    }
                    photoEntryDao.markAsSynced(photo.id, remoteUrl = downloadUrl.orEmpty())
                }
            }
        }

        Log.d("SyncRepository", "Synced ${tally.synced} pending items to Firebase, ${tally.failed} failed")
        if (tally.failed > 0) throw PendingUploadsException(tally.synced, tally.failed)
        tally.synced
    }

    companion object {
        private const val ANONYMOUS_USER_ID = "anonymous"
        private const val REMOTE_TIMEOUT_MS = 20_000L
        //el jpg pesa mas que un documento y tarda mas en subir
        private const val PHOTO_UPLOAD_TIMEOUT_MS = 90_000L
    }
}
