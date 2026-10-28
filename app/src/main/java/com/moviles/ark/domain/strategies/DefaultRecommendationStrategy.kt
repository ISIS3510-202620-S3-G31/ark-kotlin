package com.moviles.ark.domain.strategies

import com.moviles.ark.domain.models.ToolModel

/**
 * Concrete recommendation strategy that surfaces tools in their default catalog order.
 */
class DefaultRecommendationStrategy : RecommendationStrategy {
    override fun recommend(
        tools: List<ToolModel>,
        context: RecommendationContext
    ): List<ToolModel> {
        return tools
    }
}
