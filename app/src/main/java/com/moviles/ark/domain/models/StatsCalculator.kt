package com.moviles.ark.domain.models

import com.moviles.ark.domain.composite.Emotion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

//clase de dominio que procesa los datos brutos y calcula las estadisticas e insights del usuario
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

        //ultimos 7 dias desde hace 6 dias hasta hoy
        val weekDateKeys = mutableListOf<String>()
        for (i in 0..6) {
            val daysAgo = 6 - i
            val tempCal = Calendar.getInstance()
            tempCal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            weekDateKeys.add(dateFormat.format(tempCal.time))
        }
        val dayLabels = lastSevenDayLabels()

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
                //usamos la intensidad dominante del dia en lugar de un promedio plano
                val dominantIntensity = dayIntensitiesList[i].groupingBy { it }.eachCount().maxByOrNull { it.value }?.key?.toFloat()
                    ?: (dayIntensitiesList[i].maxOrNull()?.toFloat() ?: 0f)
                weeklyIntensities[dayLabel] = dominantIntensity
            } else {
                weeklyIntensities[dayLabel] = 0f
            }
        }

        val topEmotion = allEmotionsCount.maxByOrNull { it.value }?.key

        //llamamos al motor de hallazgos utiles que creo el companero
        //si el hallazgo no trae emocion propia se usa la mas frecuente para filtrar el catalogo
        val allInsights = insightsEngine.generateInsights(
            checkIns = checkIns,
            interactions = interactions
        ).map { if (it.emotion == null) it.copy(emotion = topEmotion) else it }

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
            allInsights = allInsights,
            emotionFrequencies = allEmotionsCount
        )
    }

    companion object {
        //nombres cortos de los ultimos 7 dias, de hace 6 dias hasta hoy; la grafica semanal los recorre en este orden
        //porque el mapa que vuelve de firestore no conserva el orden en que se guardaron los dias
        fun lastSevenDayLabels(): List<String> {
            val dayNameFormat = SimpleDateFormat("EEE", Locale.ENGLISH)
            return (6 downTo 0).map { daysAgo ->
                val tempCal = Calendar.getInstance()
                tempCal.add(Calendar.DAY_OF_YEAR, -daysAgo)
                dayNameFormat.format(tempCal.time)
            }
        }
    }
}
