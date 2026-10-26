package com.moviles.ark.data.local

import android.util.Log
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.models.InsightModel
import com.moviles.ark.domain.models.StatsModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

//guarda en cacheDir/stats_cache.json las ultimas estadisticas que llegaron de firestore (#147)
//asi la pantalla de stats pinta algo de inmediato al entrar, incluso sin red
class StatsDiskCache(cacheDir: File) {
    private val file = File(cacheDir, FILE_NAME)

    //devuelve null si no hay archivo, si es de otro usuario o si esta danado
    suspend fun read(userId: String): StatsModel? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            val json = JSONObject(file.readText())
            if (json.optString("userId") != userId) return@withContext null
            statsFromJson(json)
        } catch (e: Exception) {
            //archivo corrupto: se borra para que el proximo refresh escriba uno limpio
            Log.w("StatsDiskCache", "Corrupt stats cache, deleting it", e)
            file.delete()
            null
        }
    }

    //escribe primero un temporal y luego lo renombra, para no dejar un json a medias si la app se cierra
    suspend fun write(userId: String, stats: StatsModel) = withContext(Dispatchers.IO) {
        try {
            val json = statsToJson(stats).put("userId", userId).put("savedAt", System.currentTimeMillis())
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.e("StatsDiskCache", "Could not write stats cache", e)
        }
    }

    companion object {
        const val FILE_NAME = "stats_cache.json"

        //los mapas van como listas de pares para conservar el orden de los dias y de la torta
        fun statsToJson(stats: StatsModel): JSONObject {
            val weeklyIntensities = JSONArray()
            for ((day, value) in stats.weeklyIntensities) {
                weeklyIntensities.put(JSONObject().put("day", day).put("value", value.toDouble()))
            }
            val weeklyEmotions = JSONArray()
            for ((day, emotion) in stats.weeklyEmotions) {
                weeklyEmotions.put(JSONObject().put("day", day).put("emotion", emotion.name))
            }
            val toolUsage = JSONArray()
            for ((tool, percentage) in stats.toolUsagePercentages) {
                toolUsage.put(JSONObject().put("tool", tool).put("percentage", percentage.toDouble()))
            }
            val allInsights = JSONArray()
            stats.allInsights.forEach { allInsights.put(insightToJson(it)) }

            return JSONObject()
                .put("totalCheckIns", stats.totalCheckIns)
                .put("topEmotion", stats.topEmotion?.name ?: JSONObject.NULL)
                .put("weeklyIntensities", weeklyIntensities)
                .put("weeklyEmotions", weeklyEmotions)
                .put("toolUsagePercentages", toolUsage)
                .put("currentInsight", stats.currentInsight?.let { insightToJson(it) } ?: JSONObject.NULL)
                .put("allInsights", allInsights)
        }

        //lanza excepcion si faltan campos obligatorios; read() la convierte en null
        fun statsFromJson(json: JSONObject): StatsModel {
            val weeklyIntensities = LinkedHashMap<String, Float>()
            val intensities = json.getJSONArray("weeklyIntensities")
            for (i in 0 until intensities.length()) {
                val item = intensities.getJSONObject(i)
                weeklyIntensities[item.getString("day")] = item.getDouble("value").toFloat()
            }
            val weeklyEmotions = LinkedHashMap<String, Emotion>()
            val emotions = json.getJSONArray("weeklyEmotions")
            for (i in 0 until emotions.length()) {
                val item = emotions.getJSONObject(i)
                val emotion = parseEmotion(item.optString("emotion")) ?: continue
                weeklyEmotions[item.getString("day")] = emotion
            }
            val toolUsage = LinkedHashMap<String, Float>()
            val tools = json.getJSONArray("toolUsagePercentages")
            for (i in 0 until tools.length()) {
                val item = tools.getJSONObject(i)
                toolUsage[item.getString("tool")] = item.getDouble("percentage").toFloat()
            }
            val allInsights = mutableListOf<InsightModel>()
            val insights = json.optJSONArray("allInsights") ?: JSONArray()
            for (i in 0 until insights.length()) {
                allInsights.add(insightFromJson(insights.getJSONObject(i)))
            }

            return StatsModel(
                totalCheckIns = json.getInt("totalCheckIns"),
                topEmotion = parseEmotion(json.optString("topEmotion")),
                weeklyIntensities = weeklyIntensities,
                weeklyEmotions = weeklyEmotions,
                toolUsagePercentages = toolUsage,
                currentInsight = json.optJSONObject("currentInsight")?.let { insightFromJson(it) },
                allInsights = allInsights
            )
        }

        private fun insightToJson(insight: InsightModel): JSONObject {
            return JSONObject()
                .put("title", insight.title)
                .put("message", insight.message)
                .put("advice", insight.advice)
                .put("actionLabel", insight.actionLabel)
                .put("emotion", insight.emotion?.name ?: JSONObject.NULL)
        }

        private fun insightFromJson(json: JSONObject): InsightModel {
            return InsightModel(
                title = json.optString("title"),
                message = json.optString("message"),
                advice = json.optString("advice"),
                actionLabel = json.optString("actionLabel"),
                emotion = parseEmotion(json.optString("emotion"))
            )
        }

        //un nombre que ya no exista en el enum se ignora en vez de romper la lectura
        private fun parseEmotion(name: String?): Emotion? {
            if (name.isNullOrBlank()) return null
            return Emotion.entries.find { it.name == name }
        }
    }
}
