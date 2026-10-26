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
    //una fuente nueva reemplaza a lo que se ve solo si no tiene menos check-ins, asi la pantalla no salta hacia atras (#135)
    fun canReplace(shown: StatsModel?): Boolean {
        return shown == null || totalCheckIns >= shown.totalCheckIns
    }
}
