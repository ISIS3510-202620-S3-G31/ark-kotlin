package com.moviles.ark.domain.models

import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.strategies.MoodBasedRecommendationStrategy

//modelo simple de un hallazgo util para la tarjeta visual
data class InsightModel(
    val title: String = "",
    val message: String = "",
    val advice: String = "",
    val actionLabel: String = "",
    //emocion detectada en el hallazgo; con ella se filtra el catalogo al tocar la tarjeta
    val emotion: Emotion? = null
) {
    //primera categoria que la recomendacion por animo asocia a la emocion
    fun toolCategory(): ToolCategory? {
        val detected = emotion ?: return null
        return MoodBasedRecommendationStrategy.mapEmotionToToolCategories(detected).firstOrNull()
    }
}
