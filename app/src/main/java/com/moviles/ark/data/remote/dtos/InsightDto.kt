package com.moviles.ark.data.remote.dtos

import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.models.InsightModel

//dto del hallazgo que va dentro de users/{uid}/stats_summary/latest (campo currentInsight)
data class InsightDto(
    val title: String = "",
    val message: String = "",
    val advice: String = "",
    val actionLabel: String = ""
)

//dto del documento users/{uid}/useful_insights/{titulo}: el mismo hallazgo con la fecha en que se guardo
data class UsefulInsightDto(
    val title: String = "",
    val message: String = "",
    val advice: String = "",
    val actionLabel: String = "",
    //fecha en milisegundos
    val savedAt: Long = 0L
)

//de modelo de dominio a dto del resumen
fun InsightModel.toDto(): InsightDto {
    return InsightDto(
        title = title,
        message = message,
        advice = advice,
        actionLabel = actionLabel
    )
}

//de modelo de dominio a dto del historial de hallazgos
fun InsightModel.toUsefulInsightDto(savedAt: Long): UsefulInsightDto {
    return UsefulInsightDto(
        title = title,
        message = message,
        advice = advice,
        actionLabel = actionLabel,
        savedAt = savedAt
    )
}

//de dto a modelo de dominio; la emocion no viaja en el hallazgo, es la emocion principal del resumen
fun InsightDto.toDomain(emotion: Emotion?): InsightModel {
    return InsightModel(
        title = title,
        message = message,
        advice = advice,
        actionLabel = actionLabel.ifBlank { DEFAULT_INSIGHT_ACTION_LABEL },
        emotion = emotion
    )
}

//texto del boton cuando el documento no lo trae
private const val DEFAULT_INSIGHT_ACTION_LABEL = "Explore Tools"
