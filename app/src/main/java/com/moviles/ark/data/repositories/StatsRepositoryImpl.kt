package com.moviles.ark.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.models.StatsModel
import com.moviles.ark.domain.repositories.StatsRepository
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StatsRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : StatsRepository {
    @Suppress("UNCHECKED_CAST")
    override suspend fun getUserStats(): Result<StatsModel> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: "anonymous"
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val dayNameFormat = SimpleDateFormat("EEE", Locale.ENGLISH)

        //calcular los ultimos 7 dias parados en el dia de hoy (desde hace 6 dias hasta hoy)
        val weekDateKeys = mutableListOf<String>()
        val dayLabels = mutableListOf<String>()
        for (i in 0..6) {
            val daysAgo = 6 - i
            val tempCal = Calendar.getInstance()
            tempCal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            weekDateKeys.add(dateFormat.format(tempCal.time))
            dayLabels.add(dayNameFormat.format(tempCal.time))
        }
        val todayKey = weekDateKeys.last()

        //consulta si ya hay un resumen de stats guardado en firestore
        val cachedSummaryDoc = try {
            firestore.collection("users")
                .document(currentUserId)
                .collection("stats_summary")
                .document("latest")
                .get()
                .await()
        } catch (e: Exception) {
            null
        }

        //consulta los checkins emocionales de firestore
        val checkInsSnapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("mood_checkins")
            .get()
            .await()
        val checkInDocs = checkInsSnapshot.documents
        val totalCheckIns = checkInDocs.size

        //consulta las entradas de herramientas de firestore
        val toolsSnapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("tool_entries")
            .get()
            .await()
        val toolDocs = toolsSnapshot.documents
        val totalToolEntries = toolDocs.size

        //si el cache existe, es de hoy y ni los checkins ni las herramientas han cambiado, usa el cache
        if (cachedSummaryDoc != null && cachedSummaryDoc.exists()) {
            val cachedTotal = cachedSummaryDoc.getLong("totalCheckIns")?.toInt() ?: -1
            val cachedToolsTotal = cachedSummaryDoc.getLong("totalToolEntries")?.toInt() ?: -1
            val cachedDayKey = cachedSummaryDoc.getString("lastDayKey") ?: ""
            if (cachedTotal == totalCheckIns && cachedToolsTotal == totalToolEntries && cachedDayKey == todayKey) {
                val cachedTopEmotion = cachedSummaryDoc.getString("topEmotion")?.let { parseEmotionSafe(it) }
                val cachedIntensitiesRaw = cachedSummaryDoc.get("weeklyIntensities") as? Map<String, Number> ?: emptyMap()
                val cachedEmotionsRaw = cachedSummaryDoc.get("weeklyEmotions") as? Map<String, String> ?: emptyMap()
                val cachedToolPercentagesRaw = cachedSummaryDoc.get("toolUsagePercentages") as? Map<String, Number> ?: emptyMap()

                val cachedIntensities = LinkedHashMap<String, Float>()
                for (day in dayLabels) {
                    cachedIntensities[day] = cachedIntensitiesRaw[day]?.toFloat() ?: 0f
                }
                val cachedEmotions = LinkedHashMap<String, Emotion>()
                for ((day, emName) in cachedEmotionsRaw) {
                    cachedEmotions[day] = parseEmotionSafe(emName)
                }
                val cachedTools = cachedToolPercentagesRaw.mapValues { it.value.toFloat() }

                return@runCatching StatsModel(
                    totalCheckIns = cachedTotal,
                    topEmotion = cachedTopEmotion,
                    weeklyIntensities = cachedIntensities,
                    weeklyEmotions = cachedEmotions,
                    toolUsagePercentages = if (cachedTools.isNotEmpty()) cachedTools else computeToolPercentages(toolDocs)
                )
            }
        }

        //si no hay cache valido, calcula las estadisticas desde los documentos
        val dayEmotionsList = Array(7) { mutableListOf<Emotion>() }
        val dayIntensitiesList = Array(7) { mutableListOf<Int>() }
        val allEmotionsCount = mutableMapOf<Emotion, Int>()

        for (doc in checkInDocs) {
            val timestamp = doc.getLong("timestamp") ?: continue
            val dateKey = dateFormat.format(Date(timestamp))
            val emotionsRaw = doc.get("emotions") as? List<Map<String, Any>> ?: emptyList()

            val indexInWeek = weekDateKeys.indexOf(dateKey)
            for (em in emotionsRaw) {
                val rawName = em["name"] as? String ?: continue
                val emotionEnum = parseEmotionSafe(rawName)
                val intensity = (em["intensity"] as? Number)?.toInt() ?: 3

                if (indexInWeek in 0..6) {
                    dayEmotionsList[indexInWeek].add(emotionEnum)
                    dayIntensitiesList[indexInWeek].add(intensity)
                }
                allEmotionsCount[emotionEnum] = allEmotionsCount.getOrDefault(emotionEnum, 0) + 1
            }
        }

        val weeklyIntensities = LinkedHashMap<String, Float>()
        val weeklyEmotions = LinkedHashMap<String, Emotion>()

        for (i in 0..6) {
            val dayLabel = dayLabels[i]
            if (dayEmotionsList[i].isNotEmpty()) {
                val dominant = dayEmotionsList[i].groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                if (dominant != null) {
                    weeklyEmotions[dayLabel] = dominant
                }
                weeklyIntensities[dayLabel] = dayIntensitiesList[i].average().toFloat()
            } else {
                weeklyIntensities[dayLabel] = 0f
            }
        }

        val mostFrequent = allEmotionsCount.maxByOrNull { it.value }?.key
        val toolPercentages = computeToolPercentages(toolDocs)

        val computedStats = StatsModel(
            totalCheckIns = totalCheckIns,
            topEmotion = mostFrequent,
            weeklyIntensities = weeklyIntensities,
            weeklyEmotions = weeklyEmotions,
            toolUsagePercentages = toolPercentages
        )

        //guarda el resumen de estadisticas en firestore con timestamp
        saveUserStatsInternal(currentUserId, computedStats, totalToolEntries, todayKey)

        Log.d("StatsRepository", "Computed and saved stats: total=$totalCheckIns, topEmotion=$mostFrequent")
        return@runCatching computedStats
    }

    override suspend fun saveUserStats(stats: StatsModel): Result<Unit> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: "anonymous"
        val todayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val toolsSnapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("tool_entries")
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
            "lastUpdated" to System.currentTimeMillis()
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

    //calcula la distribucion porcentual de herramientas a partir de los documentos
    private fun computeToolPercentages(toolDocs: List<com.google.firebase.firestore.DocumentSnapshot>): Map<String, Float> {
        val toolCountsMap = mutableMapOf<String, Int>()

        for (doc in toolDocs) {
            val toolName = doc.getString("toolName") ?: doc.getString("toolId") ?: "Breathing"
            toolCountsMap[toolName] = toolCountsMap.getOrDefault(toolName, 0) + 1
        }

        if (toolCountsMap.isEmpty()) {
            return mapOf(
                "Breathing Pacer" to 42f,
                "Photo of the Day" to 25f,
                "Achievement Jar" to 17f,
                "Blow It Out" to 16f
            )
        }

        val total = toolCountsMap.values.sum()
        val result = mutableMapOf<String, Float>()
        for ((name, count) in toolCountsMap) {
            result[name] = (count.toFloat() / total.toFloat()) * 100f
        }
        return result
    }
}
