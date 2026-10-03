package com.moviles.ark.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.domain.composite.CompoundMood
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.SingleEmotion
import com.moviles.ark.domain.models.CheckInModel
import com.moviles.ark.domain.models.StatsCalculator
import com.moviles.ark.domain.models.StatsModel
import com.moviles.ark.domain.models.ToolInteraction
import com.moviles.ark.domain.repositories.StatsRepository
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatsRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val statsCalculator: StatsCalculator = StatsCalculator()
) : StatsRepository {
    @Suppress("UNCHECKED_CAST")
    override suspend fun getUserStats(): Result<StatsModel> = runCatching {
        val currentUserId = auth.currentUser?.uid ?: "anonymous"
        val todayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

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
            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
            toolInteractions.add(
                ToolInteraction(
                    userId = currentUserId,
                    toolId = toolId,
                    duration = 60,
                    interactionType = "touch",
                    timestamp = Date(timestamp)
                )
            )
        }

        val toolPercentages = computeToolPercentages(toolDocs)

        //la logica de calculo de estadisticas e insights vive en StatsCalculator (dominio)
        val computedStats = statsCalculator.calculateStats(
            checkIns = checkInModels,
            interactions = toolInteractions,
            toolPercentages = toolPercentages
        )

        //guarda el resumen de estadisticas e insights historicos en firestore
        saveUserStatsInternal(currentUserId, computedStats, totalToolEntries, todayKey)

        Log.d("StatsRepository", "Calculated and persisted stats with ${computedStats.allInsights.size} insights: total=$totalCheckIns")
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
        val currentInsightMap = stats.currentInsight?.let {
            hashMapOf(
                "title" to it.title,
                "message" to it.message,
                "advice" to it.advice,
                "actionLabel" to it.actionLabel
            )
        }

        val data = hashMapOf(
            "totalCheckIns" to stats.totalCheckIns,
            "totalToolEntries" to totalTools,
            "topEmotion" to stats.topEmotion?.name,
            "weeklyIntensities" to stats.weeklyIntensities,
            "weeklyEmotions" to stats.weeklyEmotions.mapValues { it.value.name },
            "toolUsagePercentages" to stats.toolUsagePercentages,
            "currentInsight" to currentInsightMap,
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

            //persiste los hallazgos calculados en la subcoleccion de insights historicos
            for (insight in stats.allInsights) {
                val insightDoc = hashMapOf(
                    "title" to insight.title,
                    "message" to insight.message,
                    "advice" to insight.advice,
                    "actionLabel" to insight.actionLabel,
                    "savedAt" to System.currentTimeMillis()
                )
                firestore.collection("users")
                    .document(userId)
                    .collection("useful_insights")
                    .document(insight.title.lowercase().replace(" ", "_"))
                    .set(insightDoc)
                    .await()
            }
        } catch (e: Exception) {
            Log.e("StatsRepository", "Error saving stats summary and insights to firestore", e)
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
