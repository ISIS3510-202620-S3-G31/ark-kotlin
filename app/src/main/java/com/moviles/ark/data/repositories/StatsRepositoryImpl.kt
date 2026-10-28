package com.moviles.ark.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.moviles.ark.data.local.StatsDiskCache
import com.moviles.ark.data.local.daos.EmotionCheckInDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.toDomain
import com.moviles.ark.data.remote.dtos.MoodCheckInDto
import com.moviles.ark.data.remote.dtos.StatsSummaryDto
import com.moviles.ark.data.remote.dtos.ToolInteractionDto
import com.moviles.ark.data.remote.dtos.toDomain
import com.moviles.ark.data.remote.dtos.toDto
import com.moviles.ark.domain.models.StatsCalculator
import com.moviles.ark.domain.models.StatsModel
import com.moviles.ark.domain.repositories.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatsRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    //los daos de room se quedan en el repositorio: el viewmodel nunca toca la base directo
    private val emotionCheckInDao: EmotionCheckInDao,
    private val toolRecordDao: ToolRecordDao,
    //cache en disco de las stats que llegaron de firestore
    private val statsDiskCache: StatsDiskCache? = null,
    private val statsCalculator: StatsCalculator = StatsCalculator()
) : StatsRepository {

    override suspend fun getCachedStats(): StatsModel? {
        val currentUserId = auth.currentUser?.uid ?: "anonymous"
        return statsDiskCache?.read(currentUserId)
    }

    //lee check-ins e interacciones de room en IO y calcula en Default; si la base esta vacia devuelve stats en cero
    override suspend fun getLocalStats(): Result<StatsModel> = runCatching {
        val (checkInEntities, interactionEntities) = withContext(Dispatchers.IO) {
            emotionCheckInDao.getCheckIns().first() to toolRecordDao.getInteractions().first()
        }
        withContext(Dispatchers.Default) {
            statsCalculator.calculateStats(
                checkIns = checkInEntities.map { it.toDomain() },
                interactions = interactionEntities.map { it.toDomain() },
                toolPercentages = StatsCalculator.toolUsagePercentages(interactionEntities.map { it.toolId })
            )
        }
    }

    //pide a firestore en IO (Source.SERVER: sin red falla en vez de devolver datos viejos) y actualiza el cache
    override suspend fun getUserStats(): Result<StatsModel> = withContext(Dispatchers.IO) { fetchAndCacheStats() }

    private suspend fun fetchAndCacheStats(): Result<StatsModel> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: "anonymous"
        val todayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

        //consulta primero el resumen precomputado directamente del backend en firestore
        val summary = try {
            firestore.collection("users")
                .document(currentUserId)
                .collection("stats_summary")
                .document("latest")
                .get(Source.SERVER)
                .await()
                .takeIf { it.exists() }
                ?.toDtoOrNull<StatsSummaryDto>()
        } catch (e: Exception) {
            Log.w("StatsRepository", "Could not fetch precomputed stats summary directly from server", e)
            null
        }

        //un resumen incompleto se recalcula abajo y se vuelve a guardar ya completo
        if (summary != null && summary.isComplete()) {
            val precomputedStats = summary.toDomain()

            //guarda de inmediato en el disco del telefono para cache-then-network
            statsDiskCache?.write(currentUserId, precomputedStats)
            Log.d("StatsRepository", "Loaded precomputed stats directly from backend: totalCheckIns=${summary.totalCheckIns}")
            return@runCatching precomputedStats
        }

        //consulta los checkins emocionales de firestore en caso de fallback o calculo inicial
        val checkInsSnapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("mood_checkins")
            .get(Source.SERVER)
            .await()
        val checkInDocs = checkInsSnapshot.documents
        val totalCheckIns = checkInDocs.size

        //consulta las interacciones de herramientas de firestore
        val toolsSnapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("tool_interactions")
            .get(Source.SERVER)
            .await()
        val toolDocs = toolsSnapshot.documents
        val totalToolEntries = toolDocs.size

        //si el usuario es nuevo y no tiene registros, devuelve modelo vacio para mostrar empty state ilustrado
        if (totalCheckIns == 0 && totalToolEntries == 0) {
            val emptyStats = StatsModel(
                totalCheckIns = 0,
                topEmotion = null,
                weeklyIntensities = emptyMap(),
                weeklyEmotions = emptyMap(),
                toolUsagePercentages = emptyMap(),
                lastUpdated = System.currentTimeMillis()
            )
            statsDiskCache?.write(currentUserId, emptyStats)
            return@runCatching emptyStats
        }

        //cada documento se lee con su dto, el mismo que usa el sync para subirlo
        val checkInModels = checkInDocs
            .mapNotNull { it.toDtoOrNull<MoodCheckInDto>() }
            .filter { it.timestamp > 0L }
            .map { it.toDomain() }
        val interactionDtos = toolDocs.mapNotNull { it.toDtoOrNull<ToolInteractionDto>() }
        val toolInteractions = interactionDtos.map { it.toDomain() }
        val toolPercentages = StatsCalculator.toolUsagePercentages(interactionDtos.map { it.toolId }.filter { it.isNotBlank() })

        //la logica de calculo de estadisticas vive en StatsCalculator (dominio)
        val calculated = statsCalculator.calculateStats(
            checkIns = checkInModels,
            interactions = toolInteractions,
            toolPercentages = toolPercentages
        )
        val computedStats = calculated.copy(lastUpdated = System.currentTimeMillis())

        //guarda el resumen en el disco del telefono para pintarlo al instante la proxima vez
        statsDiskCache?.write(currentUserId, computedStats)

        //guarda el resumen precomputado en firestore para futuras lecturas directas del backend
        saveUserStatsInternal(currentUserId, computedStats, totalToolEntries, todayKey)

        Log.d("StatsRepository", "Calculated and persisted stats total=$totalCheckIns")
        return@runCatching computedStats
    }

    override suspend fun saveUserStats(stats: StatsModel): Result<Unit> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: "anonymous"
        val todayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val toolsSnapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("tool_interactions")
            .get()
            .await()
        saveUserStatsInternal(currentUserId, stats, toolsSnapshot.documents.size, todayKey)
    }

    private suspend fun saveUserStatsInternal(userId: String, stats: StatsModel, totalTools: Int, dayKey: String) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("stats_summary")
                .document("latest")
                .set(stats.toDto(totalTools, dayKey))
                .await()
        } catch (e: Exception) {
            Log.e("StatsRepository", "Error saving stats summary to firestore", e)
        }
    }

    //arma el dto con los campos del documento
    //un documento con otra forma (por ejemplo uno de prueba escrito a mano) se salta en vez de tumbar toda la pantalla
    private inline fun <reified T : Any> DocumentSnapshot.toDtoOrNull(): T? =
        runCatching { toObject(T::class.java) }
            .onFailure { Log.w("StatsRepository", "Skipping document $id with an unexpected shape", it) }
            .getOrNull()
}
