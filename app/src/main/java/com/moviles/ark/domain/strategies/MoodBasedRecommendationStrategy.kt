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

        val targetCategories = emotions.flatMap { emotion ->
            mapEmotionToToolCategories(emotion)
        }.toSet()

        if (targetCategories.isEmpty()) return tools

        val (matching, nonMatching) = tools.partition { tool ->
            targetCategories.contains(tool.category)
        }

        return matching + nonMatching
    }

    companion object {
        /**
         * Maps user emotions (ANGER, FEAR, SADNESS, HAPPINESS, etc.) to strongly typed ToolCategory enum sets.
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


