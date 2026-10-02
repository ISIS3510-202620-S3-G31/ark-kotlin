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

    suspend fun fetchLastKnownLocation(): LocationResult? {
        val coords = fetchCoordinates() ?: return null
        val cityName = resolveCityName(coords.first, coords.second)
        return LocationResult(coords.first, coords.second, cityName)
    }

    private suspend fun fetchCoordinates(): Pair<Double, Double>? = suspendCancellableCoroutine { continuation ->
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val gpsLoc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLoc = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val passiveLoc = locationManager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            val bestLoc = gpsLoc ?: netLoc ?: passiveLoc
            if (bestLoc != null) {
                continuation.resume(Pair(bestLoc.latitude, bestLoc.longitude))
                return@suspendCancellableCoroutine
            }
        } catch (e: SecurityException) {
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
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
                            .addOnFailureListener { continuation.resume(null) }
                    } catch (e: SecurityException) {
                        continuation.resume(null)
                    }
                }
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }

    private suspend fun resolveCityName(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
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
            "Location captured"
        }
    }
}
