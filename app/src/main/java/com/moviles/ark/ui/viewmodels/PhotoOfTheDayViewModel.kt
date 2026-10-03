package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.models.PhotoEntryModel
import com.moviles.ark.domain.models.PhotoWeek
import com.moviles.ark.domain.models.ToolLatencyTracker
import com.moviles.ark.domain.repositories.PhotoRepository
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
    private val photoRepository: PhotoRepository,
    private val zone: TimeZone = TimeZone.getDefault(),
    private val now: () -> Long = { System.currentTimeMillis() },
    //mide cuanto tardo en abrirse la herramienta (#8); null en previews
    private val toolLatencyTracker: ToolLatencyTracker? = null
) : ViewModel() {
    //privado: solo el viewmodel lo modifica
    private val _uiState = MutableStateFlow(PhotoOfTheDayUiState())

    //publico: la pantalla solo lo escucha
    val uiState: StateFlow<PhotoOfTheDayUiState> = _uiState.asStateFlow()

    //se empieza a escuchar apenas se crea el viewmodel; si la pantalla rota no se vuelve a pedir
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
                    val time = now()
                    _uiState.update {
                        it.copy(
                            todayLabel = formatToday(time),
                            week = PhotoWeek.build(photos, time, zone),
                            todayPhoto = PhotoWeek.todayPhoto(photos, time, zone),
                            isLoading = false
                        )
                    }
                }
        }
    }

    //la pantalla termino su primer dibujo: se cierra la medicion de latencia que empezo con el toque (#8)
    fun onScreenRendered() {
        toolLatencyTracker?.onToolRendered(TOOL_ID)
    }

    //la camara (#31) llama esto con la direccion de la foto que se tomo
    fun onPhotoCaptured(uri: String) {
        _uiState.update { it.copy(pendingPhotoUri = uri, errorMessage = null) }
    }

    //no deja escribir mas alla del limite del modelo
    fun onCaptionChange(newCaption: String) {
        if (newCaption.length <= PhotoEntryModel.MAX_CAPTION_LENGTH) {
            _uiState.update { it.copy(caption = newCaption) }
        }
    }

    //descarta la foto sin guardar para tomar otra
    fun onRetakeClick() {
        _uiState.update { it.copy(pendingPhotoUri = null, errorMessage = null) }
    }

    fun onSaveClick() {
        val state = _uiState.value
        val uri = state.pendingPhotoUri ?: return
        if (state.isSaving) return

        //le preguntamos al modelo si la nota cumple las reglas
        val entry = PhotoEntryModel(localFilePath = uri, caption = state.caption.trim(), takenAt = now())
        if (!entry.isValidCaption()) {
            _uiState.update { it.copy(errorMessage = "Your note can have up to ${PhotoEntryModel.MAX_CAPTION_LENGTH} characters") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = photoRepository.savePhoto(uri, entry.caption, entry.takenAt)
            result.onSuccess {
                //la foto nueva llega sola por observeWeek; aqui solo se limpia lo pendiente
                _uiState.update { it.copy(isSaving = false, pendingPhotoUri = null, caption = "") }
            }
            result.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Could not save your photo, try again") }
            }
        }
    }

    //milisegundos -> "Friday, October 2"
    private fun formatToday(millis: Long): String {
        val format = SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH)
        format.timeZone = zone
        return format.format(Date(millis))
    }

    //factory para que el viewmodel reciba el photoRepository del AppContainer
    companion object {
        //id de la herramienta en el catalogo
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
