package com.moviles.ark.domain.models

import com.moviles.ark.domain.composite.Emotion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

//clase de dominio que procesa los datos brutos y calcula las estadisticas e insights del usuario (#15 y #29)
class StatsCalculator(
    private val insightsEngine: UsefulInsightsEngine = UsefulInsightsEngine()
) {

    //calcula el modelo completo de estadisticas a partir de check-ins e interacciones
    fun calculateStats(
        checkIns: List<CheckInModel>,
        interactions: List<ToolInteraction>,
        toolPercentages: Map<String, Float> = emptyMap()
    ): StatsModel {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val dayNameFormat = SimpleDateFormat("EEE", Locale.ENGLISH)

        //ultimos 7 dias desde hace 6 dias hasta hoy
        val weekDateKeys = mutableListOf<String>()
        val dayLabels = mutableListOf<String>()
        for (i in 0..6) {
            val daysAgo = 6 - i
            val tempCal = Calendar.getInstance()
            tempCal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            weekDateKeys.add(dateFormat.format(tempCal.time))
            dayLabels.add(dayNameFormat.format(tempCal.time))
        }

        val dayEmotionsList = Array(7) { mutableListOf<Emotion>() }
        val dayIntensitiesList = Array(7) { mutableListOf<Int>() }
        val allEmotionsCount = mutableMapOf<Emotion, Int>()

        for (checkIn in checkIns) {
            val dateKey = dateFormat.format(Date(checkIn.timestamp))
            val indexInWeek = weekDateKeys.indexOf(dateKey)
            val emotions = checkIn.mood.getEmotions()
            val intensity = checkIn.mood.getIntensity().toInt().coerceIn(1, 5)

            for (em in emotions) {
                if (indexInWeek in 0..6) {
                    dayEmotionsList[indexInWeek].add(em)
                    dayIntensitiesList[indexInWeek].add(intensity)
                }
                allEmotionsCount[em] = allEmotionsCount.getOrDefault(em, 0) + 1
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

        val topEmotion = allEmotionsCount.maxByOrNull { it.value }?.key

        //llamamos al motor de hallazgos utiles que creo el companero (#28)
        val allInsights = insightsEngine.generateInsights(
            checkIns = checkIns,
            interactions = interactions
        )

        //seleccionamos el insight actual: si hay varios, rotamos dinamicamente para darle variedad al entrar
        val currentInsight = if (allInsights.isNotEmpty()) {
            val rotationIndex = ((System.currentTimeMillis() / (1000 * 60 * 5)) % allInsights.size).toInt()
            allInsights[rotationIndex]
        } else {
            null
        }

        return StatsModel(
            totalCheckIns = checkIns.size,
            topEmotion = topEmotion,
            weeklyIntensities = weeklyIntensities,
            weeklyEmotions = weeklyEmotions,
            toolUsagePercentages = toolPercentages,
            currentInsight = currentInsight,
            allInsights = allInsights
        )
    }
}
