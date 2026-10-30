package com.moviles.ark.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.data.local.daos.EmotionCheckInDao
import com.moviles.ark.data.local.entities.toDomain
import com.moviles.ark.data.local.entities.toEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.MoodCheckInDto
import com.moviles.ark.data.remote.dtos.toDomain
import com.moviles.ark.domain.models.MoodCheckInModel
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
    private val onCheckInSaved: () -> Unit = {},
    //nombre de la ciudad para un check-in que se guardo sin red; null si sigue sin red
    private val resolveCityName: suspend (Double, Double) -> String? = { _, _ -> null }
) : MoodRepository {
    override suspend fun saveCheckIn(checkIn: MoodCheckInModel): Result<Unit> = withContext(Dispatchers.IO) {
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
            val todayStartMillis = MoodCheckInModel.startOfToday()

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
                    .get()
                    .await()
            }
            snapshot != null && snapshot.documents.dailyCheckIns().isNotEmpty()
        }.recover { false }
    }

    //la racha suma los check-ins de firestore y los del telefono: uno hecho sin internet cuenta desde ese momento
    override suspend fun getCheckInTimestampsSince(fromMillis: Long): Result<List<Long>> = withContext(Dispatchers.IO) {
        runCatching {
            val currentUserId = auth.currentUser?.uid ?: return@runCatching emptyList()
            val local = emotionCheckInDao.getDailyTimestampsSince(currentUserId, fromMillis)
            //sin red firestore no responde a tiempo: se sigue solo con lo del telefono
            val remote = runCatching {
                withTimeoutOrNull(REMOTE_TIMEOUT_MILLIS) {
                    firestore.collection("users")
                        .document(currentUserId)
                        .collection("mood_checkins")
                        .whereGreaterThanOrEqualTo("timestamp", fromMillis)
                        .get()
                        .await()
                }?.documents?.dailyCheckIns()?.map { it.timestamp }
            }.getOrNull().orEmpty()
            (local + remote).distinct()
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "MoodRepository",
                action = "getCheckInTimestampsSince",
                throwable = error,
                extraKeys = mapOf("fromMillis" to fromMillis)
            )
        }
    }

    override suspend fun getLatestCheckIn(): Result<MoodCheckInModel?> = withContext(Dispatchers.IO) {
        runCatching {
            val currentUserId = auth.currentUser?.uid ?: return@runCatching null
            //primero room, asi la recomendacion del home funciona sin internet
            val local = emotionCheckInDao.getLatestCheckIn(currentUserId)?.toDomain()
            if (local != null) {
                val withCity = withCityName(local)
                //la ciudad se guarda una sola vez; asi no se vuelve a pedir cada vez que se abre el home
                if (withCity.cityName != null && local.cityName == null) {
                    emotionCheckInDao.updateCityName(currentUserId, local.timestamp, withCity.cityName)
                    onCheckInSaved()
                }
                return@runCatching withCity
            }

            //room vacio: respaldo en firestore con tiempo maximo
            val snapshot = withTimeoutOrNull(REMOTE_TIMEOUT_MILLIS) {
                firestore.collection("users")
                    .document(currentUserId)
                    .collection("mood_checkins")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(REMOTE_LATEST_LIMIT)
                    .get()
                    .await()
            }
            if (snapshot == null || snapshot.isEmpty) return@runCatching null
            //firestore arma el dto con los campos del documento y el mapper lo pasa al modelo
            snapshot.documents.dailyCheckIns().firstOrNull()?.toDomain()?.let { withCityName(it) }
        }.recover { null }
    }

    //la vista del check-in muestra la ciudad; si se guardo sin red se pide ahora, sin bloquear si sigue sin red
    private suspend fun withCityName(checkIn: MoodCheckInModel): MoodCheckInModel {
        if (!checkIn.cityName.isNullOrBlank()) return checkIn
        val latitude = checkIn.latitude ?: return checkIn
        val longitude = checkIn.longitude ?: return checkIn
        return checkIn.copy(cityName = resolveCityName(latitude, longitude))
    }

    //documentos de firestore que son check-ins del dia (sin los que guardaba el feedback viejo)
    private fun List<DocumentSnapshot>.dailyCheckIns(): List<MoodCheckInDto> =
        mapNotNull { it.toObject(MoodCheckInDto::class.java) }.filterNot { MoodCheckInModel.isPostToolNote(it.note) }

    companion object {
        //tiempo maximo para el respaldo en firestore; sin red el home no se queda esperando
        private const val REMOTE_TIMEOUT_MILLIS = 3_000L
        //se piden varios por si los mas nuevos son check-ins viejos del feedback
        private const val REMOTE_LATEST_LIMIT = 10L
    }
}
