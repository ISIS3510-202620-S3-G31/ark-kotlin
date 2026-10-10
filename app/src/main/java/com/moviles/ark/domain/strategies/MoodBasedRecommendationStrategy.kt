package com.moviles.ark.domain.strategies

import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.models.CheckInModel
import com.moviles.ark.domain.models.Tool
import com.moviles.ark.domain.models.ToolCategory

/**
 * Concrete recommendation strategy that suggests tools matching the user's latest check-in mood.
 * Tools matching the target ToolCategory enum values are placed at the top of the recommended list.
 */
class MoodBasedRecommendationStrategy(
    private val defaultCheckIn: CheckInModel? = null
) : RecommendationStrategy {

    override fun recommend(
        tools: List<Tool>,
        context: RecommendationContext
    ): List<Tool> {
        val checkIn = context.latestCheckIn ?: defaultCheckIn ?: return tools
        val emotions = checkIn.mood.getEmotions()
        if (emotions.isEmpty()) return tools
        // mejor usar el flat porqeu el map normal me deja matrices entonces mejor el flat wque me deja una lista por las dif emociones qeu tenga un mood y con eso lo vuelvo set y asi no se repiten porque no habia contemplado cuando se repetian
        // asi es más facil cuando la persona sienta muchas cosas y me da to do juntito lindo
        val targetCategories = emotions.flatMap { emotion ->
            mapEmotionToToolCategories(emotion)
        }.toSet() // osea puedo recorrer to do pero aja más fail así y no me complico con un dic tipo si tengo todas las emociones de un mood quiero tener solo una lista de categorias no 10 mil

        if (targetCategories.isEmpty()) return tools

        val (matching, nonMatching) = tools.partition { tool ->
            targetCategories.contains(tool.category)
        }

        return matching + nonMatching
    }

    companion object {
        /**
         * Maps user emotion to a direct ToolCategory enum sets por lo general es mejor dejarla como statica poorque no depende de nad es estandar y además
         * asi se puede llamar en cualqueir otra pantalla si es el caso
         */
        fun mapEmotionToToolCategories(emotion: Any): Set<ToolCategory> {
            val name = when (emotion) {
                is Emotion -> emotion.name
                is String -> emotion.uppercase()
                else -> emotion.toString().uppercase()
            }

            return when (name) {
                "ANGER", "FEAR" -> setOf(
                    ToolCategory.SOMATIC_RELEASE,
                    ToolCategory.BREATHING_REGULATION,
                    ToolCategory.GROUNDING_MINDFULNESS
                )
                "SADNESS", "DISGUST" -> setOf(
                    ToolCategory.EMOTIONAL_AWARENESS,
                    ToolCategory.BREATHING_REGULATION
                )
                "HAPPINESS", "SURPRISE" -> setOf(
                    ToolCategory.BEHAVIORAL_ACTIVATION,
                    ToolCategory.EMOTIONAL_AWARENESS
                )
                else -> emptySet()
            }
        }
    }
}


