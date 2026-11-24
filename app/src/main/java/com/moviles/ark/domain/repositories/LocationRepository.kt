package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.LocationResult

//interfaz de dominio para el acceso a la ubicacion gps
interface LocationRepository {
    //coordenadas del gps (funciona sin internet); sin permiso o con gps apagado vienen en null
    suspend fun getCurrentLocation(): LocationResult
    //nombre del lugar como "Chapinero, Bogotá, CO"; necesita internet, sin respuesta a tiempo devuelve null
    //en el mismo sitio (temblor del gps) devuelve siempre el mismo nombre
    suspend fun getCityName(latitude: Double, longitude: Double): String?
}
