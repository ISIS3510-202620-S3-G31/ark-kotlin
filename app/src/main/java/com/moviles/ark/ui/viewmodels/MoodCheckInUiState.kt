package com.moviles.ark.ui.viewmodels

import com.moviles.ark.data.local.sensors.LocationStatus
import com.moviles.ark.domain.composite.Emotion
import java.util.Locale

//estado inmutable de la pantalla de check-in con soporte para coordenadas gps y nombre de la ciudad
data class MoodCheckInUiState(
    val selectedEmotions: Set<Emotion> = emptySet(),
    val activeEmotion: Emotion? = null,
    val intensity: Float = 3f,
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val cityName: String? = null,
    //null mientras se busca la ubicacion (#144)
    val locationStatus: LocationStatus? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    //dialogo amable cuando el gps esta apagado o no hay permiso (#112)
    val showLocationDisabledDialog: Boolean = false
) {
    //texto del chip de ubicacion; sin red no hay ciudad y se muestran las coordenadas (#144)
    val locationLabel: String
        get() = when (locationStatus) {
            null -> "Detecting location..."
            LocationStatus.DISABLED -> "Location Disabled"
            LocationStatus.UNAVAILABLE -> "Location unavailable"
            LocationStatus.CAPTURED -> cityName ?: if (latitude != null && longitude != null) {
                String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
            } else {
                "Location unavailable"
            }
        }
}
