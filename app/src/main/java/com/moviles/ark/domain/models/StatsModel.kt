package com.moviles.ark.domain.models

import com.moviles.ark.domain.composite.Emotion

//modelo de dominio principal de estadisticas usando directamente los enums y tipos nativos (#15 y #29)
data class StatsModel(
    val totalCheckIns: Int,
    val topEmotion: Emotion?,
    val weeklyIntensities: Map<String, Float>,
    val weeklyEmotions: Map<String, Emotion>,
    val toolUsagePercentages: Map<String, Float>,
    val currentInsight: InsightModel? = null,
    val allInsights: List<InsightModel> = emptyList(),
    val emotionFrequencies: Map<Emotion, Int> = emptyMap()
) {
    //una fuente nueva reemplaza a lo que se ve solo si no tiene menos check-ins o es mas reciente (#135, #147, #158)
    fun canReplace(shown: StatsModel?): Boolean {
        if (shown == null) return true
        if (lastUpdated > 0L && shown.lastUpdated > 0L) {
            return lastUpdated >= shown.lastUpdated || totalCheckIns >= shown.totalCheckIns
        }
        return totalCheckIns >= shown.totalCheckIns
    }
}
