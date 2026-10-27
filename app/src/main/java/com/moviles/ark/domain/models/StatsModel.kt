package com.moviles.ark.domain.models

import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.SingleEmotion

//modelo de dominio principal de estadisticas usando directamente los enums y tipos nativos
data class StatsModel(
    val totalCheckIns: Int,
    val topEmotion: Emotion?,
    val weeklyIntensities: Map<String, Float>,
    val weeklyEmotions: Map<String, Emotion>,
    val toolUsagePercentages: Map<String, Float>,
    val currentInsight: InsightModel? = null,
    val allInsights: List<InsightModel> = emptyList(),
    val emotionFrequencies: Map<Emotion, Int> = emptyMap(),
    val lastUpdated: Long = 0L
) {
    //una fuente nueva reemplaza a lo que se ve solo si no tiene menos check-ins o es mas reciente
    fun canReplace(shown: StatsModel?): Boolean {
        if (shown == null) return true
        if (lastUpdated > 0L && shown.lastUpdated > 0L) {
            return lastUpdated >= shown.lastUpdated || totalCheckIns >= shown.totalCheckIns
        }
        return totalCheckIns >= shown.totalCheckIns
    }

    //sin check-ins ni herramientas usadas se muestra la vista vacia
    fun isEmpty(): Boolean = totalCheckIns == 0 && toolUsagePercentages.isEmpty()

    //emociones de la mas frecuente a la menos, con su porcentaje del total
    fun emotionDistribution(): List<EmotionShare> {
        val total = emotionRecordCount().coerceAtLeast(1)
        return emotionFrequencies.entries
            .sortedByDescending { it.value }
            .map { (emotion, count) ->
                val fraction = (count.toFloat() / total).coerceIn(0f, 1f)
                EmotionShare(emotion, count, (count.toFloat() / total * 100f).toInt(), fraction)
            }
    }

    //cuantas emociones se registraron en total
    fun emotionRecordCount(): Int = emotionFrequencies.values.sum()

    companion object {
        //alto de la barra de un dia: la intensidad del dia sobre la escala de 1 a 5
        fun intensityRatio(intensity: Float): Float = (intensity / SingleEmotion.MAX_INTENSITY).coerceIn(0f, 1f)
    }
}

//una emocion en la distribucion: cuantas veces aparece y que porcentaje es
data class EmotionShare(
    val emotion: Emotion,
    val count: Int,
    val percentage: Int,
    //lo mismo que percentage pero de 0 a 1, para el largo de la barra
    val fraction: Float
)
