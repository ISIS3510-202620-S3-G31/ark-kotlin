package com.moviles.ark.domain.strategies

import com.moviles.ark.domain.models.Tool

/**
 * Concrete recommendation strategy that surfaces tools in their default catalog order.
 */
class DefaultRecommendationStrategy : RecommendationStrategy {
    override fun recommend(
        tools: List<Tool>,
        context: RecommendationContext
    ): List<Tool> {
        return tools
    }
}
