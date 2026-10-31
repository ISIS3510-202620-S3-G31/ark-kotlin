package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.LocationStatus
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.models.EmotionSelection
import com.moviles.ark.domain.models.MoodCheckInModel

//estado inmutable de la pantalla de check-in con soporte para coordenadas gps y nombre de la ciudad
data class MoodCheckInUiState(
    //emociones elegidas, cada una con su intensidad, y la que ajusta el slider
    val selection: EmotionSelection = EmotionSelection(),
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val cityName: String? = null,
    //null mientras se busca la ubicacion
    val locationStatus: LocationStatus? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    //aviso al guardar: subido o guardado en el telefono hasta que vuelva la red
    val savedNotice: String? = null,
    //dialogo amable cuando el gps esta apagado o no hay permiso
    val showLocationDisabledDialog: Boolean = false,
    //indica si el usuario ya completo su unico check-in diario para bloquear nuevas entradas
    val isAlreadyCheckedInToday: Boolean = false,
    //check-in guardado del dia para desplegar en modo revision no editable
    val existingCheckIn: MoodCheckInModel? = null
) {
    val selectedEmotions: Set<Emotion> get() = selection.emotions
    val activeEmotion: Emotion? get() = selection.active
    val intensity: Float get() = selection.activeIntensity.toFloat()

    //contador de la nota: el limite y el aviso los define el modelo MoodCheckInModel
    val remainingNoteChars: Int
        get() = MoodCheckInModel.remainingNoteChars(note)
    val isNoteNearLimit: Boolean
        get() = MoodCheckInModel.isNoteNearLimit(note)
    val isNoteAtLimit: Boolean
        get() = remainingNoteChars == 0

    //texto del chip de ubicacion: solo la ciudad; sin red no hay ciudad y se dice que la ubicacion se guarda
    val locationLabel: String
        get() = when (locationStatus) {
            null -> "Detecting location..."
            LocationStatus.DISABLED -> "Location off"
            LocationStatus.UNAVAILABLE -> MoodCheckInModel.NO_LOCATION_LABEL
            LocationStatus.CAPTURED -> cityName ?: MoodCheckInModel.LOCATION_SAVED_LABEL
        }
}
