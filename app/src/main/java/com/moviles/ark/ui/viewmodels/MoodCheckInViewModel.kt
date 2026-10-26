package com.moviles.ark.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.data.local.sensors.LocationStatus
import com.moviles.ark.domain.composite.CompoundMood
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.MoodComponent
import com.moviles.ark.domain.composite.SingleEmotion
import com.moviles.ark.domain.models.CheckInModel
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
    }

    private var locationJob: Job? = null

    //init y la pantalla lo llaman al abrir; si ya se esta buscando no se repite
    fun loadCurrentLocation() {
        val repository = locationRepository ?: return
        if (locationJob?.isActive == true) return
        //se borran las coordenadas anteriores mientras se busca: nunca se guarda una ubicacion vieja (#144)
        privateUiState.value = privateUiState.value.copy(
            latitude = null,
            longitude = null,
            cityName = null,
            locationStatus = null
        )
        locationJob = viewModelScope.launch {
            //el repositorio lee el gps en Dispatchers.IO con tiempo maximo y no lanza excepciones (#144)
            val result = repository.getCurrentLocation()
            privateUiState.value = privateUiState.value.copy(
                latitude = result.latitude,
                longitude = result.longitude,
                locationStatus = result.status,
                //con el gps apagado o sin permiso se avisa con el dialogo; el check-in se guarda sin coordenadas (#112)
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
            intensity = if (isAdding) 3f else privateUiState.value.intensity,
            errorMessage = null
        )
    }

    fun onIntensityChange(newIntensity: Float) {
        privateUiState.value = privateUiState.value.copy(
            intensity = newIntensity,
            errorMessage = null
        )
    }

    fun onNoteChange(newNote: String) {
        privateUiState.value = privateUiState.value.copy(
            note = newNote,
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
        if (state.selectedEmotions.isEmpty()) {
            privateUiState.value = state.copy(errorMessage = "Please select at least one emotion")
            return false
        }
        val moodResult: MoodComponent = if (state.selectedEmotions.size == 1) {
            SingleEmotion(state.selectedEmotions.first(), state.intensity.toInt())
        } else {
            val compound = CompoundMood()
            for (emotion in state.selectedEmotions) {
                compound.add(SingleEmotion(emotion, state.intensity.toInt()))
            }
            compound
        }
        val checkIn = CheckInModel(
            mood = moodResult,
            note = state.note,
            latitude = state.latitude,
            longitude = state.longitude
        )
        if (!checkIn.isValidNote()) {
            privateUiState.value = state.copy(errorMessage = "Note cannot exceed 280 characters")
            return false
        }
        Log.d("MoodCheckIn", "CheckIn saved locally: mood=${checkIn.mood.getName()}, lat=${checkIn.latitude}, lon=${checkIn.longitude}")

        if (moodRepository != null) {
            privateUiState.value = state.copy(isLoading = true)
            viewModelScope.launch {
                val result = moodRepository.saveCheckIn(checkIn)
                if (result.isSuccess) {
                    privateUiState.value = state.copy(isLoading = false, isSaved = true, errorMessage = null)
                    onSavedCallback()
                } else {
                    val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Could not save check-in"
                    Log.e("MoodCheckIn", "Save error: $errorMsg")
                    privateUiState.value = state.copy(isLoading = false, errorMessage = errorMsg)
                }
            }
        } else {
            privateUiState.value = state.copy(isLoading = false, isSaved = true, errorMessage = null)
            onSavedCallback()
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