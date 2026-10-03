package com.moviles.ark.domain.strategies

import com.moviles.ark.domain.repositories.MoodRepository

/**
 * Interface responsible for resolving current contextual data (latest check-in, tool usage statistics)
 * needed by RecommendationStrategy implementations to personalize tool ordering.
 */
interface RecommendationContextResolver {
    suspend fun resolveContext(): RecommendationContext
}

/**
 * Default implementation of RecommendationContextResolver.
 * Resolves today's/latest check-in from MoodRepository and optional tool usage counts.
 */
class DefaultRecommendationContextResolver(
    private val moodRepository: MoodRepository? = null,
    private val usageCountsProvider: (suspend () -> Map<String, Int>)? = null
) : RecommendationContextResolver {

    override suspend fun resolveContext(): RecommendationContext {
        val latestCheckIn = moodRepository?.getLatestCheckIn()?.getOrNull()
        val usageCounts = usageCountsProvider?.invoke().orEmpty()

        return RecommendationContext(
            usageCounts = usageCounts,
            latestCheckIn = latestCheckIn
        )
    }
}
