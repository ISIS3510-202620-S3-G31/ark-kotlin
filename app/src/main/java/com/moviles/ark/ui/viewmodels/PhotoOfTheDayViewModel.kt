package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.models.PhotoDay
import com.moviles.ark.domain.models.PhotoDayState
import com.moviles.ark.domain.models.PhotoEntryModel
import com.moviles.ark.domain.models.PhotoWeek
import com.moviles.ark.domain.models.ToolLatencyTracker
import com.moviles.ark.domain.repositories.PhotoEntryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

//now y zone se pueden cambiar para fijar "hoy"; en la app son la hora y la zona del telefono
class PhotoOfTheDayViewModel(
    private val photoRepository: PhotoEntryRepository,
    private val zone: TimeZone = TimeZone.getDefault(),
    private val now: () -> Long = { System.currentTimeMillis() },
    //mide cuanto tardo en abrirse la herramienta; null en previews
    private val toolLatencyTracker: ToolLatencyTracker? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoOfTheDayUiState())
    val uiState: StateFlow<PhotoOfTheDayUiState> = _uiState.asStateFlow()

    private var weekPhotos: List<PhotoEntryModel> = emptyList()

    init {
        observeWeek()
    }

    //escucha las fotos de la semana; cada vez que se guarda una, la tira y la foto de hoy se actualizan solas
    private fun observeWeek() {
        val starts = PhotoWeek.dayStarts(now(), zone)
        viewModelScope.launch {
            photoRepository.getPhotosBetween(starts.first(), starts.last())
                .catch {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load your photos") }
                }
                .collect { photos ->
                    weekPhotos = photos
                    val time = now()
                    _uiState.update { current ->
                        current.copy(
                            todayLabel = formatToday(time),
                            week = PhotoWeek.build(photos, time, zone),
                            todayPhoto = PhotoWeek.todayPhoto(photos, time, zone),
                            isLoading = false
                        )
                    }
                }
        }
    }

    //la pantalla termino su primer dibujo: se cierra la medicion de latencia que empezo con el toque
    fun onScreenRendered() {
        toolLatencyTracker?.onToolRendered(TOOL_ID)
    }

    //la camara captura una foto: si hoy ya existia una foto, se reemplaza la anterior por la nueva
    fun onPhotoCaptured(uri: String) {
        val previousTodayPhoto = _uiState.value.todayPhoto
        _uiState.update { it.copy(pendingPhotoUri = uri, caption = "", isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            if (previousTodayPhoto != null) {
                photoRepository.deletePhoto(previousTodayPhoto.id)
            }
            val result = photoRepository.savePhoto(uri, "", now())
            result.onSuccess {
                _uiState.update { it.copy(isSaving = false, pendingPhotoUri = null, caption = "", isPhotoSaved = true) }
            }
            result.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Could not save your photo, try again") }
            }
        }
    }

    //selecciona un dia de la tira semanal; si es un dia pasado, se visualiza en modo lectura
    fun onSelectDay(day: PhotoDay) {
        if (day.state == PhotoDayState.TODAY) {
            _uiState.update { it.copy(isPastDaySelected = false, selectedDayPhoto = null, selectedDayName = null) }
            return
        }
        val photo = PhotoWeek.photoForDay(weekPhotos, day.dayIndex, now(), zone)
        _uiState.update {
            it.copy(
                isPastDaySelected = true,
                selectedDayPhoto = photo,
                selectedDayName = day.name
            )
        }
    }

    //regresa a la vista del dia de hoy
    fun onBackToToday() {
        _uiState.update { it.copy(isPastDaySelected = false, selectedDayPhoto = null, selectedDayName = null) }
    }

    //muestra u oculta el dialogo modal para confirmar la eliminacion
    fun onShowDeleteDialog(show: Boolean) {
        _uiState.update { it.copy(showDeleteDialog = show) }
    }

    //elimina la foto de hoy con confirmacion del usuario
    fun onDeleteTodayPhoto() {
        val todayPhoto = _uiState.value.todayPhoto ?: return
        _uiState.update { it.copy(isSaving = true, showDeleteDialog = false, errorMessage = null) }
        viewModelScope.launch {
            val result = photoRepository.deletePhoto(todayPhoto.id)
            result.onSuccess {
                _uiState.update { it.copy(isSaving = false, todayPhoto = null) }
            }
            result.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Could not delete your photo, try again") }
            }
        }
    }

    //descarta la foto pendiente sin guardar para tomar otra
    fun onRetakeClick() {
        _uiState.update { it.copy(pendingPhotoUri = null, errorMessage = null) }
    }

    fun onSaveClick() {
        val state = _uiState.value
        val uri = state.pendingPhotoUri ?: return
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val previousTodayPhoto = state.todayPhoto
            if (previousTodayPhoto != null) {
                photoRepository.deletePhoto(previousTodayPhoto.id)
            }
            val result = photoRepository.savePhoto(uri, "", now())
            result.onSuccess {
                _uiState.update { it.copy(isSaving = false, pendingPhotoUri = null, caption = "", isPhotoSaved = true) }
            }
            result.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Could not save your photo, try again") }
            }
        }
    }

    fun onCaptionChange(newCaption: String) {
        //mantiene compatibilidad pero no muestra campos en pantalla
    }

    fun onNavigatedToFeedback() {
        _uiState.update { it.copy(isPhotoSaved = false) }
    }

    //milisegundos -> "Friday, October 2"
    private fun formatToday(millis: Long): String {
        val format = SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH)
        format.timeZone = zone
        return format.format(Date(millis))
    }

    //factory para que el viewmodel reciba el photoRepository del AppContainer
    companion object {
        const val TOOL_ID = "photo_of_the_day"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                PhotoOfTheDayViewModel(
                    photoRepository = app.container.photoRepository,
                    toolLatencyTracker = app.container.toolLatencyTracker
                )
            }
        }
    }
}
