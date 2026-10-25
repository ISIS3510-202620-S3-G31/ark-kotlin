package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.CachedRecommendation

//cache en disco de la ultima recomendacion del home (#159)
//el dominio no sabe que detras hay un archivo json; la implementacion vive en data
interface RecommendationRepository {
    suspend fun saveLastRecommendation(recommendation: CachedRecommendation): Result<Unit>

    //null si no hay archivo o si esta danado
    suspend fun getLastRecommendation(): CachedRecommendation?
}
