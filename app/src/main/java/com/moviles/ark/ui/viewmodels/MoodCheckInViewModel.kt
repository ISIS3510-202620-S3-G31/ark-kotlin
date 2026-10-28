package com.moviles.ark.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.models.LocationStatus
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.MoodFactory
import com.moviles.ark.domain.composite.SingleEmotion
import com.moviles.ark.domain.models.MoodCheckInModel
import com.moviles.ark.domain.repositories.LocationRepository
import com.moviles.ark.domain.repositories.MoodRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MoodCheckInViewModel(
    private val locationRepository: LocationRepository? = null,
    private val moodRepository: MoodRepository? = null
) : ViewModel() {
    private val privateUiState = MutableStateFlow(MoodCheckInUiState())
    val uiState: StateFlow<MoodCheckInUiState> = privateUiState.asStateFlow()

    init {
        loadCurrentLocation()
        checkTodayCheckIn()
    }

    private var locationJob: Job? = null

    //verifica si el usuario ya registro su check-in en el dia actual para bloquear nuevas creaciones
    fun checkTodayCheckIn() {
        val repository = moodRepository ?: return
        viewModelScope.launch {
            val hasChecked = repository.hasCheckedInToday().getOrDefault(false)
            if (hasChecked) {
                val latest = repository.getLatestCheckIn().getOrNull()
                privateUiState.value = privateUiState.value.copy(
                    isAlreadyCheckedInToday = true,
                    existingCheckIn = latest
                )
            }
        }
    }

    //limpia el borrador en memoria al cerrar o descartar el dialogo sin mutar contadores ni base de datos
    fun resetDraft() {
        val wasChecked = privateUiState.value.isAlreadyCheckedInToday
        val existing = privateUiState.value.existingCheckIn
        privateUiState.value = MoodCheckInUiState(
            isAlreadyCheckedInToday = wasChecked,
            existingCheckIn = existing
        )
        loadCurrentLocation()
    }

    //init y la pantalla lo llaman al abrir; si ya se esta buscando no se repite
    fun loadCurrentLocation() {
        val repository = locationRepository ?: return
        if (locationJob?.isActive == true) return
        //se borran las coordenadas anteriores mientras se busca: nunca se guarda una ubicacion vieja
        privateUiState.value = privateUiState.value.copy(
            latitude = null,
            longitude = null,
            cityName = null,
            locationStatus = null
        )
        locationJob = viewModelScope.launch {
            //el repositorio lee el gps en Dispatchers.IO con tiempo maximo y no lanza excepciones
            val result = repository.getCurrentLocation()
            privateUiState.value = privateUiState.value.copy(
                latitude = result.latitude,
                longitude = result.longitude,
                locationStatus = result.status,
                //con el gps apagado o sin permiso se avisa con el dialogo; el check-in se guarda sin coordenadas
                showLocationDisabledDialog = result.status == LocationStatus.DISABLED
            )
            Log.d("MoodCheckIn", "GPS loaded: status=${result.status}, lat=${result.latitude}, lon=${result.longitude}")
            val latitude = result.latitude ?: return@launch
            val longitude = result.longitude ?: return@launch
            //la ciudad llega despues y solo si hay red; mientras tanto el chip muestra las coordenadas
            val cityName = repository.getCityName(latitude, longitude) ?: return@launch
            val current = privateUiState.value
            if (current.latitude == latitude && current.longitude == longitude) {
                privateUiState.value = current.copy(cityName = cityName)
            }
        }
    }

    fun dismissLocationWarningDialog() {
        privateUiState.value = privateUiState.value.copy(
            showLocationDisabledDialog = false
        )
    }

    fun onEmotionToggle(emotion: Emotion) {
        val currentEmotions = privateUiState.value.selectedEmotions
        val isAdding = !currentEmotions.contains(emotion)
        val newEmotions = if (isAdding) {
            currentEmotions + emotion
        } else {
            currentEmotions - emotion
        }
        val newActive = if (newEmotions.contains(emotion)) {
            emotion
        } else {
            newEmotions.lastOrNull()
        }
        privateUiState.value = privateUiState.value.copy(
            selectedEmotions = newEmotions,
            activeEmotion = newActive,
            intensity = if (isAdding) SingleEmotion.DEFAULT_INTENSITY.toFloat() else privateUiState.value.intensity,
            errorMessage = null
        )
    }

    fun onIntensityChange(newIntensity: Float) {
        privateUiState.value = privateUiState.value.copy(
            intensity = newIntensity,
            errorMessage = null
        )
    }

    //el modelo recorta la nota al limite, por ejemplo si se pega un texto largo
    fun onNoteChange(newNote: String) {
        privateUiState.value = privateUiState.value.copy(
            note = MoodCheckInModel.limitNote(newNote),
            errorMessage = null
        )
    }

    fun onLocationUpdated(latitude: Double, longitude: Double, cityName: String? = null) {
        privateUiState.value = privateUiState.value.copy(
            latitude = latitude,
            longitude = longitude,
            cityName = cityName,
            locationStatus = LocationStatus.CAPTURED
        )
    }

    fun saveCheckIn(onSavedCallback: () -> Unit = {}): Boolean {
        val state = privateUiState.value
        //si ya se registro hoy, se bloquea la creacion de un nuevo check-in
        if (state.isAlreadyCheckedInToday) {
            privateUiState.value = state.copy(errorMessage = MoodCheckInModel.ALREADY_CHECKED_IN_ERROR)
            return false
        }
        //evita envios multiples simultaneos
        if (state.isLoading || state.isSaved) {
            return false
        }
        //el modelo arma el animo (una emocion o varias) y valida el check-in
        val checkIn = MoodCheckInModel(
            mood = MoodFactory.fromEmotions(state.selectedEmotions, state.intensity.toInt()),
            note = state.note,
            latitude = state.latitude,
            longitude = state.longitude
        )
        val validationError = checkIn.validationError()
        if (validationError != null) {
            privateUiState.value = state.copy(errorMessage = validationError)
            return false
        }
        Log.d("MoodCheckIn", "CheckIn saved locally: mood=${checkIn.mood.getName()}, lat=${checkIn.latitude}, lon=${checkIn.longitude}")

        val repository = moodRepository
        if (repository != null) {
            privateUiState.value = state.copy(isLoading = true)
            viewModelScope.launch {
                val result = repository.saveCheckIn(checkIn)
                if (result.isSuccess) {
                    privateUiState.value = state.copy(
                        isLoading = false,
                        isSaved = true,
                        isAlreadyCheckedInToday = true,
                        existingCheckIn = checkIn,
                        errorMessage = null
                    )
                    //el contador de estadisticas solo se actualiza tras la insercion exitosa en base de datos
                    onSavedCallback()
                } else {
                    val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Could not save check-in"
                    Log.e("MoodCheckIn", "Save error: $errorMsg")
                    privateUiState.value = state.copy(isLoading = false, errorMessage = errorMsg)
                }
            }
        } else {
            //sin repositorio local no se puede insertar en room; nunca se incrementa contador prematuramente
            privateUiState.value = state.copy(isLoading = false, errorMessage = "Check-in storage is unavailable")
            return false
        }
        return true
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                MoodCheckInViewModel(
                    locationRepository = app.container.locationRepository,
                    moodRepository = app.container.moodRepository
                )
            }
        }
    }
}
