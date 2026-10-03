package com.moviles.ark.data.repositories

import com.moviles.ark.data.local.sensors.Location
import com.moviles.ark.data.local.sensors.LocationResult
import com.moviles.ark.domain.repositories.LocationRepository

class LocationRepositoryImpl(
    private val locationDataSource: Location
) : LocationRepository {

    override suspend fun getCurrentLocation(): LocationResult? {
        return locationDataSource.fetchLastKnownLocation()
    }
}