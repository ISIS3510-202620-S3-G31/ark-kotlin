package com.moviles.ark.domain.models

import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.MoodFactory
import com.moviles.ark.domain.composite.SingleEmotion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

//clase de dominio que procesa los datos brutos y calcula las estadisticas del usuario
class StatsCalculator {

    //calcula el modelo completo de estadisticas a partir de check-ins e interacciones
    //solo cuentan los check-ins diarios: los que guardaba el feedback viejo se ignoran
    fun calculateStats(
        checkIns: List<MoodCheckInModel>,
        interactions: List<ToolInteractionModel>,
        toolPercentages: Map<String, Float> = emptyMap(),
        lastUpdated: Long = System.currentTimeMillis()
    ): StatsModel {
        val dailyCheckIns = checkIns.filterNot { it.isPostToolFeedback() }
        val dateFormat = SimpleDateFormat(DATE_KEY_PATTERN, Locale.getDefault())
        val weekKeys = lastSevenDays().map { it.key }

        //por dia de la semana: cada emocion registrada con su propia intensidad
        val dayParts = weekKeys.associateWith { mutableListOf<SingleEmotion>() }
        val allEmotionsCount = mutableMapOf<Emotion, Int>()

        for (checkIn in dailyCheckIns) {
            val parts = MoodFactory.partsOf(checkIn.mood)
            dayParts[dateFormat.format(Date(checkIn.timestamp))]?.addAll(parts)
            for (part in parts) {
                allEmotionsCount[part.emotion] = allEmotionsCount.getOrDefault(part.emotion, 0) + 1
            }
        }

        //la llave de cada dia es su fecha (yyyyMMdd): un resumen de otro dia nunca cae en el dia equivocado
        val weeklyIntensities = LinkedHashMap<String, Float>()
        val weeklyEmotions = LinkedHashMap<String, Emotion>()
        for ((dayKey, parts) in dayParts) {
            //emocion que mas se registro ese dia y la intensidad mas alta que tuvo
            val dominant = parts.groupingBy { it.emotion }.eachCount().maxByOrNull { it.value }?.key
            if (dominant == null) {
                weeklyIntensities[dayKey] = 0f
            } else {
                weeklyEmotions[dayKey] = dominant
                weeklyIntensities[dayKey] = parts.filter { it.emotion == dominant }.maxOf { it.intensity }.toFloat()
            }
        }

        return StatsModel(
            totalCheckIns = dailyCheckIns.size,
            topEmotion = allEmotionsCount.maxByOrNull { it.value }?.key,
            weeklyIntensities = weeklyIntensities,
            weeklyEmotions = weeklyEmotions,
            toolUsagePercentages = toolPercentages,
            emotionFrequencies = allEmotionsCount,
            lastUpdated = lastUpdated
        )
    }

    companion object {
        //porcentaje de uso de cada herramienta a partir de sus nombres o ids (firestore o room)
        fun toolUsagePercentages(rawToolNames: List<String>): Map<String, Float> {
            val counts = rawToolNames.filter { it.isNotBlank() }.groupingBy { displayToolName(it) }.eachCount()
            if (counts.isEmpty()) return emptyMap()
            val total = counts.values.sum().toFloat()
            return counts.mapValues { (_, count) -> count / total * 100f }
        }

        //nombre visible de una herramienta; junta los alias viejos (espanol, ids cortos) bajo un mismo nombre
        fun displayToolName(raw: String): String {
            return when (raw.lowercase().replace("-", "_").trim()) {
                "breathing_pacer", "breathing", "respiracion" -> "Breathing Pacer"
                "photo_of_the_day", "photo", "foto" -> "Photo of the Day"
                "achievement_jar", "jar", "frasco" -> "Achievement Jar"
                "blow_it_out", "blow", "sopla" -> "Blow It Out"
                else -> raw.split("_", "-").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                }
            }
        }

        //hoy y los 6 dias anteriores, sin importar que dia de la semana sea hoy; la grafica los recorre en este orden
        //key es la fecha (yyyyMMdd) con la que se guardan los datos y label el nombre corto que se ve ("Mon")
        fun lastSevenDays(now: Long = System.currentTimeMillis()): List<WeekDay> {
            val keyFormat = SimpleDateFormat(DATE_KEY_PATTERN, Locale.getDefault())
            val labelFormat = SimpleDateFormat("EEE", Locale.ENGLISH)
            return (6 downTo 0).map { daysAgo ->
                val day = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, -daysAgo)
                }.time
                WeekDay(keyFormat.format(day), labelFormat.format(day))
            }
        }

        private const val DATE_KEY_PATTERN = "yyyyMMdd"
    }
}

//un dia de la grafica semanal: la fecha con la que se buscan los datos y el nombre que se muestra
data class WeekDay(val key: String, val label: String)
