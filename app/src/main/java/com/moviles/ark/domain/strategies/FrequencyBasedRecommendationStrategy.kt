package com.moviles.ark.domain.strategies

import com.moviles.ark.domain.models.Tool

/**
 * Concrete recommendation strategy that surfaces tools the user completes most often.
 * Tools with higher usage counts appear first; ties or uncompleted tools retain relative catalog order.
 */
class FrequencyBasedRecommendationStrategy(
    private val defaultUsageCounts: Map<String, Int> = emptyMap()
) : RecommendationStrategy {

    override fun recommend(
        tools: List<Tool>,
        context: RecommendationContext
    ): List<Tool> {
        val usageMap = if (context.usageCounts.isNotEmpty()) {
            context.usageCounts
        } else {
            defaultUsageCounts
        }

        if (usageMap.isEmpty()) return tools

        return tools.sortedByDescending { tool ->
            usageMap[tool.id] ?: 0
        }
    }
}
