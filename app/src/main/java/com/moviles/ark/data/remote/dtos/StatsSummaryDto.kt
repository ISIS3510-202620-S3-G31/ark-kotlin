package com.moviles.ark.data.remote.dtos

import com.moviles.ark.domain.models.StatsModel

//dto del documento users/{uid}/stats_summary/latest en firestore
//resumen ya calculado de stats que la app escribe para el backend; la pantalla siempre calcula con los datos
//los numeros con decimales van como Double porque firestore los guarda asi
data class StatsSummaryDto(
    val totalCheckIns: Int = 0,
    val totalToolEntries: Int = 0,
    //nombre del enum Emotion; null si todavia no hay check-ins
    val topEmotion: String? = null,
    //dia -> intensidad promedio de 1 a 5
    val weeklyIntensities: Map<String, Double> = emptyMap(),
    //dia -> nombre de la emocion del dia
    val weeklyEmotions: Map<String, String> = emptyMap(),
    //herramienta -> porcentaje de uso
    val toolUsagePercentages: Map<String, Double> = emptyMap(),
    //nombre de la emocion -> veces que aparece; null en resumenes guardados antes de existir el campo
    val emotionFrequencies: Map<String, Int>? = null,
    //campo residual en backend en caso de existir en firestore
    val currentInsight: Any? = null,
    //dia del calculo en formato yyyyMMdd
    val lastDayKey: String = "",
    //fecha del calculo en milisegundos
    val lastUpdated: Long = 0L
)

//de modelo de dominio a dto; si el modelo no trae fecha de calculo se usa la de ahora
fun StatsModel.toDto(totalToolEntries: Int, dayKey: String): StatsSummaryDto {
    return StatsSummaryDto(
        totalCheckIns = totalCheckIns,
        totalToolEntries = totalToolEntries,
        topEmotion = topEmotion?.name,
        weeklyIntensities = weeklyIntensities.mapValues { it.value.toDouble() },
        weeklyEmotions = weeklyEmotions.mapValues { it.value.name },
        toolUsagePercentages = toolUsagePercentages.mapValues { it.value.toDouble() },
        emotionFrequencies = emotionFrequencies.mapKeys { it.key.name },
        currentInsight = null,
        lastDayKey = dayKey,
        lastUpdated = if (lastUpdated > 0L) lastUpdated else System.currentTimeMillis()
    )
}
