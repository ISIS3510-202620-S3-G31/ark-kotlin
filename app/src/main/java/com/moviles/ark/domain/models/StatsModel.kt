package com.moviles.ark.domain.models

import com.moviles.ark.domain.composite.Emotion

//modelo de dominio principal de estadisticas usando directamente los enums y tipos nativos
data class StatsModel(
    val totalCheckIns: Int,
    val topEmotion: Emotion?,
    val weeklyIntensities: Map<String, Float>,
    val weeklyEmotions: Map<String, Emotion>,
    val toolUsagePercentages: Map<String, Float>,
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
}
