package com.moviles.ark.data.local.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import android.location.Location as AndroidLocation
import com.moviles.ark.domain.models.LocationResult
import com.moviles.ark.domain.models.LocationStatus

//el gps del telefono funciona sin datos ni wifi; solo el nombre de la ciudad (geocoder) necesita red
class Location(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    //hilo propio para las respuestas del gps, asi nunca se usa el hilo principal
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    //nunca lanza excepcion: sin permiso, con gps apagado o sin respuesta a tiempo devuelve coordenadas null
    suspend fun fetchCurrentLocation(): LocationResult = withContext(Dispatchers.IO) {
        try {
            if (!hasLocationPermission()) return@withContext LocationResult(status = LocationStatus.DISABLED)
            val manager = locationManager ?: return@withContext LocationResult(status = LocationStatus.UNAVAILABLE)
            if (!LocationManagerCompat.isLocationEnabled(manager)) {
                return@withContext LocationResult(status = LocationStatus.DISABLED)
            }
            val location = freshLastKnownLocation(manager)
                ?: withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) { requestCurrentLocation(manager) }
            if (location == null) {
                LocationResult(status = LocationStatus.UNAVAILABLE)
            } else {
                LocationResult(location.latitude, location.longitude, LocationStatus.CAPTURED)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            //por ejemplo SecurityException si quitan el permiso mientras se lee
            com.moviles.ark.data.remote.CrashlyticsHelper.logNonFatal(
                componentName = "LocationSensor",
                action = "fetchCurrentLocation",
                throwable = e
            )
            LocationResult(status = if (hasLocationPermission()) LocationStatus.UNAVAILABLE else LocationStatus.DISABLED)
        }
    }

    private fun hasLocationPermission(): Boolean {
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

    //la ultima posicion guardada solo sirve si es reciente: nunca se usan coordenadas viejas
    private fun freshLastKnownLocation(manager: LocationManager): AndroidLocation? {
        return enabledProviders(manager)
            .mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    null
                }
            }
            .filter { isFresh(it) }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }

    private fun isFresh(location: AndroidLocation): Boolean {
        val ageMillis = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        return ageMillis in 0..MAX_LOCATION_AGE_MILLIS
    }

    //gps primero porque no necesita red; network solo si el gps esta apagado o no hay permiso fino
    private fun enabledProviders(manager: LocationManager): List<String> {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val candidates = if (hasFine) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(LocationManager.NETWORK_PROVIDER)
        }
        return candidates.filter { provider ->
            try {
                manager.isProviderEnabled(provider)
            } catch (e: Exception) {
                false
            }
        }
    }

    //pide una posicion nueva a todos los proveedores a la vez y usa la primera que llegue:
    //bajo techo el gps no fija nunca y se comeria todo el timeout antes de preguntarle a la red
    //si el timeout la cancela, se cancelan tambien todas las peticiones
    private suspend fun requestCurrentLocation(manager: LocationManager): AndroidLocation? = coroutineScope {
        val requests = enabledProviders(manager).map { provider ->
            async { requestFromProvider(manager, provider) }
        } + async { requestFromFused() }
        try {
            firstNonNull(requests)
        } finally {
            requests.forEach { it.cancel() }
        }
    }

    //espera hasta que alguna peticion traiga posicion; null solo si todas respondieron sin posicion
    private suspend fun firstNonNull(requests: List<Deferred<AndroidLocation?>>): AndroidLocation? {
        val pending = requests.toMutableList()
        while (pending.isNotEmpty()) {
            val (finished, location) = select {
                pending.forEach { request -> request.onAwait { request to it } }
            }
            if (location != null) return location
            pending.remove(finished)
        }
        return null
    }

    private suspend fun requestFromProvider(manager: LocationManager, provider: String): AndroidLocation? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(manager, provider, cancellationSignal, callbackExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (e: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private suspend fun requestFromFused(): AndroidLocation? = suspendCancellableCoroutine { continuation ->
        val cancellationTokenSource = CancellationTokenSource()
        continuation.invokeOnCancellation { cancellationTokenSource.cancel() }
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
                .addOnFailureListener { error ->
                    com.moviles.ark.data.remote.CrashlyticsHelper.logNonFatal(
                        componentName = "LocationSensor",
                        action = "getCurrentLocation_failure",
                        throwable = error
                    )
                    if (continuation.isActive) continuation.resume(null)
                }
        } catch (e: Exception) {
            //sin play services o sin permiso: se sigue sin coordenadas
            if (continuation.isActive) continuation.resume(null)
        }
    }

    //nombre de la ciudad con geocoder; necesita red, asi que tiene tiempo maximo y sin respuesta devuelve null
    suspend fun resolveCityName(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = withTimeoutOrNull(GEOCODER_TIMEOUT_MILLIS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                if (continuation.isActive) continuation.resume(addresses)
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        })
                    }
                } else {
                    //la version vieja bloquea el hilo; runInterruptible lo corta cuando se acaba el tiempo
                    @Suppress("DEPRECATION")
                    runInterruptible { geocoder.getFromLocation(latitude, longitude, 1) }
                }
            }
            val address = addresses?.firstOrNull() ?: return@withContext null
            val city = address.locality ?: address.subAdminArea ?: address.adminArea
            val country = address.countryName
            if (city != null && country != null) "$city, $country" else city ?: country
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
            null
        }
    }

    companion object {
        //tiempo maximo esperando una posicion nueva del gps
        private const val LOCATION_TIMEOUT_MILLIS = 8_000L
        //una posicion guardada de hace mas de 2 minutos ya no es "donde estoy ahora"
        private const val MAX_LOCATION_AGE_MILLIS = 2 * 60 * 1000L
        //sin red el geocoder no responde; la pantalla muestra las coordenadas
        private const val GEOCODER_TIMEOUT_MILLIS = 3_000L
    }
}
