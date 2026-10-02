package com.moviles.ark.data.repositories

import com.moviles.ark.data.local.sensors.Location

class LocationRepositoryImpl(
    private val locationDataSource: Location
) : LocationRepository {

    override suspend fun getCurrentLocation(): Pair<Double, Double>? {
        return locationDataSource.fetchLastKnownLocation()
    }
}