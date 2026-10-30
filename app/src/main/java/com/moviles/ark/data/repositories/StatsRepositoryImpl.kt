package com.moviles.ark.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.moviles.ark.data.local.StatsDiskCache
import com.moviles.ark.data.local.daos.StatsDailyVisitDao
import com.moviles.ark.data.local.entities.StatsDailyVisitEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.MoodCheckInDto
import com.moviles.ark.data.remote.dtos.hasReadableEmotions
import com.moviles.ark.data.remote.dtos.ToolInteractionDto
import com.moviles.ark.data.remote.dtos.toDomain
import com.moviles.ark.data.remote.dtos.toDto
import com.moviles.ark.domain.models.MoodCheckInModel
import com.moviles.ark.domain.models.StatsCalculator
import com.moviles.ark.domain.models.StatsModel
import com.moviles.ark.domain.repositories.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatsRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    //cache en disco (cacheDir) de las ultimas stats que llegaron de firestore: es lo unico que se muestra sin red
    private val statsDiskCache: StatsDiskCache? = null,
    private val statsCalculator: StatsCalculator = StatsCalculator(),
    private val statsDailyVisitDao: StatsDailyVisitDao? = null,
    private val onVisitRecorded: () -> Unit = {},
    private val now: () -> Long = { System.currentTimeMillis() }
) : StatsRepository {

    override suspend fun getCachedStats(): StatsModel? {
        val currentUserId = auth.currentUser?.uid ?: "anonymous"
        return statsDiskCache?.read(currentUserId)
    }

    //pide a firestore en IO (Source.SERVER: sin red falla en vez de devolver datos viejos) y actualiza el cache
    override suspend fun getUserStats(): Result<StatsModel> = withContext(Dispatchers.IO) { fetchAndCacheStats() }

    private suspend fun fetchAndCacheStats(): Result<StatsModel> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: "anonymous"
        val todayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

        //siempre se calcula con los check-ins e interacciones de firestore: el resumen guardado podia ser de otro dia,
        //contaba los check-ins del feedback viejo y mezclaba nombres de herramientas; aqui solo se vuelve a escribir
        val checkInsSnapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("mood_checkins")
            .get(Source.SERVER)
            .await()
        //solo los check-ins diarios; los que guardaba el feedback viejo no cuentan
        val dailyDtos = checkInsSnapshot.documents
            .mapNotNull { it.toDtoOrNull<MoodCheckInDto>() }
            .filter { it.timestamp > 0L && !MoodCheckInModel.isPostToolNote(it.note) }
        //un documento sin emociones legibles cuenta como check-in, pero no suma a ninguna emocion
        val checkInModels = dailyDtos.filter { it.hasReadableEmotions() }.map { it.toDomain() }
        val totalCheckIns = dailyDtos.size
        Log.d("StatsRepository", "Daily check-ins: $totalCheckIns, without readable emotions: ${totalCheckIns - checkInModels.size}")

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
        val interactionDtos = toolDocs.mapNotNull { it.toDtoOrNull<ToolInteractionDto>() }
        val toolInteractions = interactionDtos.map { it.toDomain() }
        val toolPercentages = StatsCalculator.toolUsagePercentages(interactionDtos.map { it.toolId }.filter { it.isNotBlank() })

        //la logica de calculo de estadisticas vive en StatsCalculator (dominio)
        val calculated = statsCalculator.calculateStats(
            checkIns = checkInModels,
            interactions = toolInteractions,
            toolPercentages = toolPercentages
        )
        //lastUpdated es el momento en que estas stats salieron de firestore; la pantalla lo muestra tal cual
        val computedStats = calculated.copy(totalCheckIns = totalCheckIns, lastUpdated = System.currentTimeMillis())

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

    //registra la entrada del usuario a la pantalla de estadisticas e incrementa el contador del dia
    override suspend fun recordStatsVisit(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val currentUserId = auth.currentUser?.uid ?: "anonymous"
            val timestamp = now()
            val todayKey = todayDateKey(timestamp)
            val dao = statsDailyVisitDao ?: return@runCatching 0

            // 1. Obtener registro previo del dia o iniciar en 0
            val existing = dao.getVisitForDate(currentUserId, todayKey)
            val newCount = (existing?.count ?: 0) + 1

            // 2. Guardar primero en Room SQLite (offline-first) con isSynced = false
            val entity = StatsDailyVisitEntity(
                userId = currentUserId,
                dateKey = todayKey,
                count = newCount,
                lastUpdatedAt = timestamp,
                isSynced = false
            )
            dao.insertOrUpdate(entity)

            // 3. Avisar al SyncManager para que lo suba; no se espera a firestore
            //sin internet esa espera no terminaba y la pantalla de estadisticas se quedaba cargando
            onVisitRecorded()

            newCount
        }.onFailure { error ->
            Log.e("StatsRepository", "Error recording daily stats visit", error)
            CrashlyticsHelper.logNonFatal(
                componentName = "StatsRepository",
                action = "recordStatsVisit",
                throwable = error
            )
        }
    }

    //obtiene cuantas veces ha entrado el usuario en el dia de hoy
    override suspend fun getTodayStatsVisitsCount(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val currentUserId = auth.currentUser?.uid ?: "anonymous"
            val dao = statsDailyVisitDao ?: return@runCatching 0
            val todayKey = todayDateKey(now())
            dao.getCountForDate(currentUserId, todayKey) ?: 0
        }
    }

    private fun todayDateKey(timestamp: Long): String {
        return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(timestamp))
    }

    //arma el dto con los campos del documento
    //un documento con otra forma (por ejemplo uno de prueba escrito a mano) se salta en vez de tumbar toda la pantalla
    private inline fun <reified T : Any> DocumentSnapshot.toDtoOrNull(): T? =
        runCatching { toObject(T::class.java) }
            .onFailure { Log.w("StatsRepository", "Skipping document $id with an unexpected shape", it) }
            .getOrNull()
}
