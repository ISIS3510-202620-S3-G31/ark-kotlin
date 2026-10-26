package com.moviles.ark.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.moviles.ark.data.local.StatsDiskCache
import com.moviles.ark.data.local.daos.EmotionCheckInDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.toDomain
import com.moviles.ark.domain.composite.CompoundMood
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.SingleEmotion
import com.moviles.ark.domain.models.CheckInModel
import com.moviles.ark.domain.models.StatsCalculator
import com.moviles.ark.domain.models.StatsModel
import com.moviles.ark.domain.models.ToolInteraction
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
                toolPercentages = computeToolPercentages(interactionEntities.map { it.toolId })
            )
        }
    }

    //pide a firestore en IO (Source.SERVER: sin red falla en vez de devolver datos viejos) y actualiza el cache (#147, #158)
    override suspend fun getUserStats(): Result<StatsModel> = withContext(Dispatchers.IO) { fetchAndCacheStats() }

    @Suppress("UNCHECKED_CAST")
    private suspend fun fetchAndCacheStats(): Result<StatsModel> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: "anonymous"
        val todayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

        //consulta primero el resumen precomputado directamente del backend en firestore
        val summaryDoc = try {
            firestore.collection("users")
                .document(currentUserId)
                .collection("stats_summary")
                .document("latest")
                .get(Source.SERVER)
                .await()
        } catch (e: Exception) {
            Log.w("StatsRepository", "Could not fetch precomputed stats summary directly from server", e)
            null
        }

        if (summaryDoc != null && summaryDoc.exists()) {
            val totalCheckIns = (summaryDoc.getLong("totalCheckIns") ?: 0L).toInt()
            val topEmotion = summaryDoc.getString("topEmotion")?.let { parseEmotionSafe(it) }
            val lastUpdated = summaryDoc.getLong("lastUpdated") ?: System.currentTimeMillis()

            val rawIntensities = summaryDoc.get("weeklyIntensities") as? Map<String, Any> ?: emptyMap()
            val weeklyIntensities = LinkedHashMap<String, Float>()
            for ((day, value) in rawIntensities) {
                weeklyIntensities[day] = (value as? Number)?.toFloat() ?: 0f
            }

            val rawEmotions = summaryDoc.get("weeklyEmotions") as? Map<String, Any> ?: emptyMap()
            val weeklyEmotions = LinkedHashMap<String, Emotion>()
            for ((day, emName) in rawEmotions) {
                weeklyEmotions[day] = parseEmotionSafe(emName.toString())
            }

            val rawPercentages = summaryDoc.get("toolUsagePercentages") as? Map<String, Any> ?: emptyMap()
            val toolPercentages = LinkedHashMap<String, Float>()
            for ((tool, pct) in rawPercentages) {
                toolPercentages[tool] = (pct as? Number)?.toFloat() ?: 0f
            }

            val precomputedStats = StatsModel(
                totalCheckIns = totalCheckIns,
                topEmotion = topEmotion,
                weeklyIntensities = weeklyIntensities,
                weeklyEmotions = weeklyEmotions,
                toolUsagePercentages = toolPercentages,
                lastUpdated = lastUpdated
            )

            //guarda de inmediato en el disco del telefono para cache-then-network
            statsDiskCache?.write(currentUserId, precomputedStats)
            Log.d("StatsRepository", "Loaded precomputed stats directly from backend: totalCheckIns=$totalCheckIns")
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

        //transforma los documentos crudos de firestore a modelos de dominio
        val checkInModels = mutableListOf<CheckInModel>()
        for (doc in checkInDocs) {
            val timestamp = doc.getLong("timestamp") ?: continue
            val emotionsRaw = doc.get("emotions") as? List<Map<String, Any>> ?: emptyList()
            val note = doc.getString("note") ?: ""
            val lat = doc.getDouble("latitude")
            val lon = doc.getDouble("longitude")

            val parsedEmotions = mutableListOf<SingleEmotion>()
            for (em in emotionsRaw) {
                val rawName = em["name"] as? String ?: continue
                val emotionEnum = parseEmotionSafe(rawName)
                val intensity = (em["intensity"] as? Number)?.toInt() ?: 3
                parsedEmotions.add(SingleEmotion(emotion = emotionEnum, intensity = intensity))
            }

            val moodComponent = when {
                parsedEmotions.size > 1 -> {
                    val compound = CompoundMood()
                    parsedEmotions.forEach { compound.add(it) }
                    compound
                }
                parsedEmotions.size == 1 -> parsedEmotions[0]
                else -> SingleEmotion(Emotion.HAPPINESS, 3)
            }

            checkInModels.add(
                CheckInModel(
                    mood = moodComponent,
                    note = note,
                    latitude = lat,
                    longitude = lon,
                    timestamp = timestamp
                )
            )
        }

        val toolInteractions = mutableListOf<ToolInteraction>()
        for (doc in toolDocs) {
            val toolId = doc.getString("toolId") ?: "breathing_pacer"
            val timestampDate = doc.getDate("timestamp")
                ?: doc.getLong("timestamp")?.let { Date(it) }
                ?: Date()
            val duration = (doc.getLong("duration") ?: 60L).toInt()
            val interactionType = doc.getString("interactionType") ?: "touch"
            val intensity = (doc.getLong("intensity") ?: 0L).toInt()
            val complete = doc.getBoolean("complete") ?: true
            val value = (doc.getLong("value") ?: 0L).toInt()
            val description = doc.getString("description") ?: ""

            toolInteractions.add(
                ToolInteraction(
                    userId = currentUserId,
                    toolId = toolId,
                    duration = duration,
                    interactionType = interactionType,
                    intensity = intensity,
                    complete = complete,
                    value = value,
                    timestamp = timestampDate,
                    description = description
                )
            )
        }

        val toolPercentages = computeToolPercentages(toolDocs.mapNotNull { it.getString("toolName") ?: it.getString("toolId") })

        //la logica de calculo de estadisticas e insights vive en StatsCalculator (dominio)
        val calculated = statsCalculator.calculateStats(
            checkIns = checkInModels,
            interactions = toolInteractions,
            toolPercentages = toolPercentages
        )
        val computedStats = calculated.copy(lastUpdated = System.currentTimeMillis())

        //guarda el resumen en el disco del telefono para pintarlo al instante la proxima vez
        statsDiskCache?.write(currentUserId, computedStats)

        //guarda el resumen precomputado e insights en firestore para futuras lecturas directas del backend
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
        val data = hashMapOf(
            "totalCheckIns" to stats.totalCheckIns,
            "totalToolEntries" to totalTools,
            "topEmotion" to stats.topEmotion?.name,
            "weeklyIntensities" to stats.weeklyIntensities,
            "weeklyEmotions" to stats.weeklyEmotions.mapValues { it.value.name },
            "toolUsagePercentages" to stats.toolUsagePercentages,
            "lastDayKey" to dayKey,
            "lastUpdated" to if (stats.lastUpdated > 0L) stats.lastUpdated else System.currentTimeMillis()
        )
        try {
            firestore.collection("users")
                .document(userId)
                .collection("stats_summary")
                .document("latest")
                .set(data)
                .await()
        } catch (e: Exception) {
            Log.e("StatsRepository", "Error saving stats summary to firestore", e)
        }
    }

    //convierte el string de emocion en el enum oficial del patron composite
    private fun parseEmotionSafe(name: String): Emotion {
        return try {
            Emotion.valueOf(name.uppercase())
        } catch (e: Exception) {
            Emotion.HAPPINESS
        }
    }

    //calcula la distribucion porcentual de herramientas a partir de sus nombres o ids (firestore o room)
    private fun computeToolPercentages(rawToolNames: List<String>): Map<String, Float> {
        val toolCountsMap = mutableMapOf<String, Int>()

        for (rawName in rawToolNames) {
            if (rawName.isNotBlank()) {
                val toolName = formatToolName(rawName)
                toolCountsMap[toolName] = toolCountsMap.getOrDefault(toolName, 0) + 1
            }
        }

        if (toolCountsMap.isEmpty()) {
            return emptyMap()
        }

        val total = toolCountsMap.values.sum()
        val result = mutableMapOf<String, Float>()
        for ((name, count) in toolCountsMap) {
            result[name] = (count.toFloat() / total.toFloat()) * 100f
        }
        return result
    }

    private fun formatToolName(raw: String): String {
        return when (raw.lowercase().replace("-", "_").trim()) {
            "breathing_pacer", "breathing", "respiracion" -> "Breathing Pacer"
            "photo_of_the_day", "photo", "foto" -> "Photo of the Day"
            "achievement_jar", "jar", "frasco" -> "Achievement Jar"
            "blow_it_out", "blow", "sopla" -> "Blow It Out"
            else -> raw.split("_", "-").joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }
    }
}
