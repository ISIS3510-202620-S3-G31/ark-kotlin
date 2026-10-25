package com.moviles.ark.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.MoodCheckInDto
import com.moviles.ark.data.remote.dtos.toDomain
import com.moviles.ark.data.remote.dtos.toDto
import com.moviles.ark.domain.models.CheckInModel
import com.moviles.ark.domain.repositories.MoodRepository
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class MoodRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : MoodRepository {
    override suspend fun saveCheckIn(checkIn: CheckInModel): Result<Unit> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: "anonymous"
        //guarda en la subcoleccion mood_checkins del usuario segun las reglas de seguridad
        //mismo dto que usa el sync (#163), asi los dos caminos escriben los mismos campos
        firestore.collection("users")
            .document(currentUserId)
            .collection("mood_checkins")
            .add(checkIn.toDto(currentUserId))
            .await()
        Log.d("MoodCheckIn", "Successfully saved checkin in users/$currentUserId/mood_checkins")
        Unit
    }.onFailure { error ->
        Log.e("MoodCheckIn", "Failed to save checkin to Firestore", error)
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

    override suspend fun hasCheckedInToday(): Result<Boolean> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: return@runCatching false
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStartMillis = calendar.timeInMillis

        val snapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("mood_checkins")
            .whereGreaterThanOrEqualTo("timestamp", todayStartMillis)
            .limit(1)
            .get()
            .await()

        !snapshot.isEmpty
    }.recover { false }

    override suspend fun getCheckInTimestampsSince(fromMillis: Long): Result<List<Long>> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: return@runCatching emptyList()
        val snapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("mood_checkins")
            .whereGreaterThanOrEqualTo("timestamp", fromMillis)
            .get()
            .await()
        snapshot.documents.mapNotNull { it.getLong("timestamp") }
    }.onFailure { error ->
        CrashlyticsHelper.logNonFatal(
            componentName = "MoodRepository",
            action = "getCheckInTimestampsSince",
            throwable = error,
            extraKeys = mapOf("fromMillis" to fromMillis)
        )
    }

    override suspend fun getLatestCheckIn(): Result<CheckInModel?> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: return@runCatching null
        val snapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("mood_checkins")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()

        if (snapshot.isEmpty) return@runCatching null
        //firestore arma el dto con los campos del documento y el mapper lo pasa al modelo (#163)
        snapshot.documents.first().toObject(MoodCheckInDto::class.java)?.toDomain()
    }.recover { null }
}

