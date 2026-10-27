package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.LocationResult

//interfaz de dominio para el acceso a la ubicacion gps
interface LocationRepository {
    //coordenadas del gps (funciona sin internet); sin permiso o con gps apagado vienen en null
    suspend fun getCurrentLocation(): LocationResult
    //nombre de la ciudad; necesita internet, sin respuesta a tiempo devuelve null
    suspend fun getCityName(latitude: Double, longitude: Double): String?
}
