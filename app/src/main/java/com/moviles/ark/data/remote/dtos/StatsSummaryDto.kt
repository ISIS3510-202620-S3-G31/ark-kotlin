package com.moviles.ark.data.remote.dtos

import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.MoodFactory
import com.moviles.ark.domain.models.StatsModel

//dto del documento users/{uid}/stats_summary/latest en firestore
//es el resumen ya calculado de stats: la pantalla lo lee directo sin bajar todos los check-ins
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
) {
    //un resumen guardado antes de existir emotionFrequencies no sirve para la tarjeta de distribucion
    //si ya hay check-ins y le falta el campo, hay que recalcularlo
    fun isComplete(): Boolean = totalCheckIns == 0 || emotionFrequencies != null
}

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

//de dto a modelo de dominio; un nombre de emocion que ya no existe cae en la emocion por defecto
fun StatsSummaryDto.toDomain(): StatsModel {
    val mainEmotion = topEmotion?.let { MoodFactory.emotionOrDefault(it) }
    //dos nombres pueden caer en la misma emocion: se suman
    val frequencies = LinkedHashMap<Emotion, Int>()
    for ((name, count) in emotionFrequencies.orEmpty()) {
        val emotion = MoodFactory.emotionOrDefault(name)
        frequencies[emotion] = frequencies.getOrDefault(emotion, 0) + count
    }
    return StatsModel(
        totalCheckIns = totalCheckIns,
        topEmotion = mainEmotion,
        weeklyIntensities = weeklyIntensities.mapValuesTo(LinkedHashMap()) { it.value.toFloat() },
        weeklyEmotions = weeklyEmotions.mapValuesTo(LinkedHashMap()) { MoodFactory.emotionOrDefault(it.value) },
        toolUsagePercentages = toolUsagePercentages.mapValuesTo(LinkedHashMap()) { it.value.toFloat() },
        emotionFrequencies = frequencies,
        lastUpdated = if (lastUpdated > 0L) lastUpdated else System.currentTimeMillis()
    )
}
