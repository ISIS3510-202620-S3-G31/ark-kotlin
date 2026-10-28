package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.LocationStatus
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.SingleEmotion
import com.moviles.ark.domain.models.MoodCheckInModel
import java.util.Locale

//estado inmutable de la pantalla de check-in con soporte para coordenadas gps y nombre de la ciudad
data class MoodCheckInUiState(
    val selectedEmotions: Set<Emotion> = emptySet(),
    val activeEmotion: Emotion? = null,
    val intensity: Float = SingleEmotion.DEFAULT_INTENSITY.toFloat(),
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val cityName: String? = null,
    //null mientras se busca la ubicacion
    val locationStatus: LocationStatus? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    //dialogo amable cuando el gps esta apagado o no hay permiso
    val showLocationDisabledDialog: Boolean = false,
    //indica si el usuario ya completo su unico check-in diario para bloquear nuevas entradas
    val isAlreadyCheckedInToday: Boolean = false,
    //check-in guardado del dia para desplegar en modo revision no editable
    val existingCheckIn: MoodCheckInModel? = null
) {
    //contador de la nota: el limite y el aviso los define el modelo MoodCheckInModel
    val remainingNoteChars: Int
        get() = MoodCheckInModel.remainingNoteChars(note)
    val isNoteNearLimit: Boolean
        get() = MoodCheckInModel.isNoteNearLimit(note)
    val isNoteAtLimit: Boolean
        get() = remainingNoteChars == 0

    //texto del chip de ubicacion; sin red no hay ciudad y se muestran las coordenadas
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
