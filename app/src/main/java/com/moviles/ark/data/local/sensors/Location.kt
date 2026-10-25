package com.moviles.ark.data.local.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val cityName: String
)

class Location(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    companion object {
        // Ventana de frescura maxima de 48 horas para reutilizar coordenadas en cache (#110)
        const val MAX_LOCATION_AGE_MILLIS = 48 * 60 * 60 * 1000L
    }

    fun hasLocationPermission(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    // 1. Verifica si el hardware del GPS o el proveedor de red estan habilitados en los ajustes del dispositivo
    fun isGpsEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    suspend fun fetchLastKnownLocation(): LocationResult? {
        val coords = fetchCoordinates() ?: return null
        val cityName = resolveCityName(coords.first, coords.second)
        return LocationResult(coords.first, coords.second, cityName)
    }

    private suspend fun fetchCoordinates(): Pair<Double, Double>? = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        // Si el GPS esta desactivado a nivel de sistema, se evita consultar o reutilizar coordenadas obsoletas en silencio
        if (!isGpsEnabled()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val currentTime = System.currentTimeMillis()

        // 2. Verificar cache reciente en LocationManager aplicando la ventana de frescura de 48 horas
        try {
            val gpsLoc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLoc = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val passiveLoc = locationManager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

            val freshCachedLocation = listOfNotNull(gpsLoc, netLoc, passiveLoc)
                .filter { (currentTime - it.time) <= MAX_LOCATION_AGE_MILLIS }
                .maxByOrNull { it.time }

            if (freshCachedLocation != null) {
                continuation.resume(Pair(freshCachedLocation.latitude, freshCachedLocation.longitude))
                return@suspendCancellableCoroutine
            }
        } catch (e: SecurityException) {
            com.moviles.ark.data.remote.CrashlyticsHelper.logNonFatal(
                componentName = "LocationSensor",
                action = "fetchCoordinates_security",
                throwable = e
            )
        }

        // 3. Si la cache es mayor a 48h o nula, consultar FusedLocationProviderClient con filtro de frescura o solicitud fresca
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null && (currentTime - location.time) <= MAX_LOCATION_AGE_MILLIS) {
                    continuation.resume(Pair(location.latitude, location.longitude))
                } else {
                    try {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { freshLocation ->
                                if (freshLocation != null) {
                                    continuation.resume(Pair(freshLocation.latitude, freshLocation.longitude))
                                } else {
                                    continuation.resume(null)
                                }
                            }
                            .addOnFailureListener { freshLocationError ->
                                com.moviles.ark.data.remote.CrashlyticsHelper.logNonFatal(
                                    componentName = "LocationSensor",
                                    action = "getCurrentLocation_failure",
                                    throwable = freshLocationError
                                )
                                continuation.resume(null)
                            }
                    } catch (e: SecurityException) {
                        com.moviles.ark.data.remote.CrashlyticsHelper.logNonFatal(
                            componentName = "LocationSensor",
                            action = "getCurrentLocation_security",
                            throwable = e
                        )
                        continuation.resume(null)
                    }
                }
            }
            .addOnFailureListener { error ->
                com.moviles.ark.data.remote.CrashlyticsHelper.logNonFatal(
                    componentName = "LocationSensor",
                    action = "fusedLocation_lastLocation_failure",
                    throwable = error
                )
                continuation.resume(null)
            }
    }

    private suspend fun resolveCityName(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val city = address.locality ?: address.subAdminArea ?: address.adminArea
                val country = address.countryName
                if (city != null && country != null) {
                    "$city, $country"
                } else {
                    city ?: country ?: "Location captured"
                }
            } else {
                "Location captured"
            }
        } catch (e: Exception) {
            com.moviles.ark.data.remote.CrashlyticsHelper.logNonFatal(
                componentName = "LocationSensor",
                action = "resolveCityName",
                throwable = e,
                extraKeys = mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude
                )
            )
            "Location captured"
        }
    }
}
