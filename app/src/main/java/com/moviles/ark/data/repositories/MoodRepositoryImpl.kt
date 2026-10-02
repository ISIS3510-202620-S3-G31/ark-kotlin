package com.moviles.ark.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.domain.models.CheckInModel
import com.moviles.ark.domain.repositories.MoodRepository
import kotlinx.coroutines.tasks.await

class MoodRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : MoodRepository {
    override suspend fun saveCheckIn(checkIn: CheckInModel): Result<Unit> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: "anonymous"
        val emotionsData = checkIn.mood.getEmotions().map { emotion ->
            mapOf(
                "name" to emotion.name,
                "intensity" to checkIn.mood.getIntensity().toInt()
            )
        }
        val checkInMap = hashMapOf(
            "userId" to currentUserId,
            "timestamp" to checkIn.timestamp,
            "latitude" to checkIn.latitude,
            "longitude" to checkIn.longitude,
            "emotions" to emotionsData,
            "note" to checkIn.note
        )
        //guarda en la subcoleccion mood_checkins del usuario segun las reglas de seguridad
        firestore.collection("users")
            .document(currentUserId)
            .collection("mood_checkins")
            .add(checkInMap)
            .await()
        Log.d("MoodCheckIn", "Successfully saved checkin in users/$currentUserId/mood_checkins")
        Unit
    }.onFailure { error ->
        Log.e("MoodCheckIn", "Failed to save checkin to Firestore", error)
    }
}
