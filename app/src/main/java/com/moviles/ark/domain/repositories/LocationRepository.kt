package com.moviles.ark.domain.repositories

import com.moviles.ark.data.local.sensors.LocationResult

//interfaz de dominio para el acceso a la ubicacion gps
interface LocationRepository {
    suspend fun getCurrentLocation(): LocationResult?
}