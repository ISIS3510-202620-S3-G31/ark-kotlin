package com.moviles.ark.domain.strategies

import com.moviles.ark.domain.models.CheckInModel
import com.moviles.ark.domain.models.Tool

/**
 * Contextual data provided to recommendation strategies to order tools.
 */
data class RecommendationContext(
    val usageCounts: Map<String, Int> = emptyMap(),
    val latestCheckIn: CheckInModel? = null
)

/**
 * Strategy pattern interface for ordering tool recommendations.
 */
interface RecommendationStrategy {
    /**
     * Orders and returns recommended tools according to the strategy algorithm.
     */
    fun recommend(
        tools: List<Tool>,
        context: RecommendationContext = RecommendationContext()
    ): List<Tool>
}
