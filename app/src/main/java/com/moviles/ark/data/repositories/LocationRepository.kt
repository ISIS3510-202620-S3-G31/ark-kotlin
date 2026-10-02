package com.moviles.ark.data.repositories

interface LocationRepository {
    suspend fun getCurrentLocation(): Pair<Double, Double>?
}