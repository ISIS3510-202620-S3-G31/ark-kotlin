package com.moviles.ark.domain.models
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Función útil para saber si ya tenemos el permiso concedido
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Obtiene la ubicación actual.
     * Si no hay permiso, ejecuta onError() directamente.
     */
    fun fetchCurrentLocation(
        onSuccess: (latitude: Double, longitude: Double) -> Unit,
        onError: () -> Unit = {}
    ) {
        // Verificamos si ya tenemos permiso concedido previamente
        if (!hasLocationPermission()) {
            onError()
            return
        }

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        onSuccess(location.latitude, location.longitude)
                    } else {
                        onError()
                    }
                }
                .addOnFailureListener {
                    onError()
                }
        } catch (e: SecurityException) {
            onError()
        }
    }
}