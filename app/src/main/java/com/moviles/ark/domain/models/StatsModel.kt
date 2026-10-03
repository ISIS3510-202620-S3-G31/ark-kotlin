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
    val allInsights: List<InsightModel> = emptyList()
)
