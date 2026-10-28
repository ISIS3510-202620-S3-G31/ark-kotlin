package com.moviles.ark.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.data.local.daos.EmotionCheckInDao
import com.moviles.ark.data.local.entities.toDomain
import com.moviles.ark.data.local.entities.toEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.MoodCheckInDto
import com.moviles.ark.data.remote.dtos.toDomain
import com.moviles.ark.domain.models.CheckInModel
import com.moviles.ark.domain.repositories.MoodRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

//el check-in se guarda primero en room y el SyncManager lo sube a firestore cuando hay internet
class MoodRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val emotionCheckInDao: EmotionCheckInDao,
    //avisa que hay un check-in pendiente para que se suba ya si hay red; no se espera
    private val onCheckInSaved: () -> Unit = {}
) : MoodRepository {
    override suspend fun saveCheckIn(checkIn: CheckInModel): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val currentUserId = auth.currentUser?.uid ?: "anonymous"
            //queda con isSynced = false; guardar no depende de la red, asi el check-in es instantaneo
            emotionCheckInDao.insertCheckIn(checkIn.toEntity(currentUserId))
            Log.d("MoodCheckIn", "Check-in saved in Room for $currentUserId, pending sync")
            onCheckInSaved()
            Unit
        }.onFailure { error ->
            Log.e("MoodCheckIn", "Failed to save checkin in Room", error)
            CrashlyticsHelper.logNonFatal(
                componentName = "MoodRepository",
                action = "saveCheckIn",
                throwable = error,
                extraKeys = mapOf(
                    "user_id" to (auth.currentUser?.uid ?: "anonymous"),
                    "has_location" to (checkIn.latitude != null && checkIn.longitude != null),
                    "emotions_count" to checkIn.mood.getEmotions().size
                )
            )
        }
    }

    override suspend fun hasCheckedInToday(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val currentUserId = auth.currentUser?.uid ?: return@runCatching false
            //el modelo define desde cuando cuenta "hoy"
            val todayStartMillis = CheckInModel.startOfToday()

            //primero room: incluye los check-ins que todavia no se han subido
            if (emotionCheckInDao.countCheckInsSince(currentUserId, todayStartMillis) > 0) return@runCatching true
            //room tiene check-ins pero ninguno de hoy: no hace falta preguntarle a firestore
            if (emotionCheckInDao.countCheckInsSince(currentUserId, 0L) > 0) return@runCatching false

            //room vacio (por ejemplo app reinstalada): respaldo en firestore con tiempo maximo, sin red da false
            val snapshot = withTimeoutOrNull(REMOTE_TIMEOUT_MILLIS) {
                firestore.collection("users")
                    .document(currentUserId)
                    .collection("mood_checkins")
                    .whereGreaterThanOrEqualTo("timestamp", todayStartMillis)
                    .limit(1)
                    .get()
                    .await()
            }
            snapshot != null && !snapshot.isEmpty
        }.recover { false }
    }

    override suspend fun getCheckInTimestampsSince(fromMillis: Long): Result<List<Long>> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: return@runCatching emptyList()
        val snapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("mood_checkins")
            .whereGreaterThanOrEqualTo("timestamp", fromMillis)
            .get()
            .await()
        snapshot.documents.mapNotNull { it.toObject(MoodCheckInDto::class.java)?.timestamp }
    }.onFailure { error ->
        CrashlyticsHelper.logNonFatal(
            componentName = "MoodRepository",
            action = "getCheckInTimestampsSince",
            throwable = error,
            extraKeys = mapOf("fromMillis" to fromMillis)
        )
    }

    override suspend fun getLatestCheckIn(): Result<CheckInModel?> = withContext(Dispatchers.IO) {
        runCatching {
            val currentUserId = auth.currentUser?.uid ?: return@runCatching null
            //primero room, asi la recomendacion del home funciona sin internet
            emotionCheckInDao.getLatestCheckIn(currentUserId)?.let { return@runCatching it.toDomain() }

            //room vacio: respaldo en firestore con tiempo maximo
            val snapshot = withTimeoutOrNull(REMOTE_TIMEOUT_MILLIS) {
                firestore.collection("users")
                    .document(currentUserId)
                    .collection("mood_checkins")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()
            }
            if (snapshot == null || snapshot.isEmpty) return@runCatching null
            //firestore arma el dto con los campos del documento y el mapper lo pasa al modelo
            snapshot.documents.first().toObject(MoodCheckInDto::class.java)?.toDomain()
        }.recover { null }
    }

    companion object {
        //tiempo maximo para el respaldo en firestore; sin red el home no se queda esperando
        private const val REMOTE_TIMEOUT_MILLIS = 3_000L
    }
}
