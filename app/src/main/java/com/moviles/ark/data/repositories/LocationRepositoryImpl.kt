package com.moviles.ark.data.repositories

import android.util.Log
import com.moviles.ark.data.local.sensors.Location
import com.moviles.ark.domain.models.LocationResult
import com.moviles.ark.domain.models.ResolvedPlace
import com.moviles.ark.domain.repositories.LocationRepository

class LocationRepositoryImpl(
    private val locationDataSource: Location
) : LocationRepository {

    //ultimo nombre resuelto en esta sesion; vive mientras la app este abierta
    @Volatile
    private var lastPlace: ResolvedPlace? = null

    override suspend fun getCurrentLocation(): LocationResult {
        return locationDataSource.fetchCurrentLocation()
    }

    //si las coordenadas caen cerca de las anteriores se devuelve el mismo nombre (el modelo decide que es "cerca"),
    //asi varios check-ins o aperturas en el mismo sitio no dan nombres distintos por el temblor del gps
    override suspend fun getCityName(latitude: Double, longitude: Double): String? {
        val previousPlace = lastPlace
        if (previousPlace != null && previousPlace.covers(latitude, longitude)) {
            Log.d(Location.TAG, "same place as before, reusing: ${previousPlace.name}")
            return previousPlace.name
        }

        //lugar nuevo: se le pregunta al geocoder y se recuerda para la proxima vez
        val placeName = locationDataSource.resolveCityName(latitude, longitude)
        if (placeName == null) return null
        lastPlace = ResolvedPlace(latitude, longitude, placeName)
        return placeName
    }
}
