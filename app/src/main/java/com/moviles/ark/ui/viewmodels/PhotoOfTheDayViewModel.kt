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
    //privado: solo el viewmodel lo modifica
    private val _uiState = MutableStateFlow(PhotoOfTheDayUiState())

    //publico: la pantalla solo lo escucha
    val uiState: StateFlow<PhotoOfTheDayUiState> = _uiState.asStateFlow()

    //momento en que se abrio la herramienta; el viewmodel vive mientras la pantalla esta abierta
    private val sessionStartMs = now()

    //se empieza a escuchar apenas se crea el viewmodel; si la pantalla rota no se vuelve a pedir
    init {
        observeWeek()
    }

    //escucha las fotos de los ultimos 7 dias; cada vez que se guarda una, la tira y la foto de hoy se actualizan solas
    private fun observeWeek() {
        val starts = PhotoWeek.dayStarts(now(), zone)
        //las de dias pasados que falten en el telefono se traen de la nube; llegan solas por el flow de room
        //hoy no se trae: la de hoy se puede borrar o repetir y la nube podria devolver una ya borrada
        viewModelScope.launch {
            photoRepository.refreshFromCloud(starts.first(), starts[starts.size - 2])
        }
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

    //la pantalla termino su primer dibujo: se cierra la medicion de latencia que empezo con el toque
    fun onScreenRendered() {
        toolLatencyTracker?.onToolRendered(TOOL_ID)
    }

    fun hasCameraPermission(): Boolean = photoRepository.hasCameraPermission()

    //prepara el archivo para la camara y devuelve su direccion para abrirla; null si no se pudo crear
    fun prepareCapture(): String? {
        val uri = photoRepository.createCaptureUri()
        _uiState.update { it.copy(captureUri = uri, errorMessage = if (uri == null) "Could not open the camera, try again" else null) }
        return uri
    }

    //respuesta de la camara: si tomo la foto se guarda la del archivo preparado
    fun onCaptureResult(success: Boolean) {
        val uri = _uiState.value.captureUri
        _uiState.update { it.copy(captureUri = null) }
        if (success && uri != null) onPhotoCaptured(uri)
    }

    //la camara llama esto con la direccion de la foto capturada; se guarda sin caption de texto
    //si hoy ya habia foto es un retake: se borra la anterior y la nueva queda como la de hoy
    fun onPhotoCaptured(uri: String) {
        val previousTodayPhoto = _uiState.value.todayPhoto
        _uiState.update { it.copy(pendingPhotoUri = uri, caption = "", isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            if (previousTodayPhoto != null) photoRepository.deletePhoto(previousTodayPhoto.id)
            val result = photoRepository.savePhoto(uri, "", now())
            result.onSuccess {
                //la foto nueva llega sola por observeWeek; aqui solo se limpia lo pendiente y avisa que se guardo
                _uiState.update { it.copy(isSaving = false, pendingPhotoUri = null, caption = "", isPhotoSaved = true) }
            }
            result.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Could not save your photo, try again") }
            }
        }
    }

    //mantiene compatibilidad (flujo sin caption)
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

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            //una foto por dia: si hoy ya habia una, la nueva la reemplaza
            state.todayPhoto?.let { photoRepository.deletePhoto(it.id) }
            val result = photoRepository.savePhoto(uri, "", now())
            result.onSuccess {
                //la foto nueva llega sola por observeWeek; aqui solo se limpia lo pendiente y avisa que se guardo
                _uiState.update { it.copy(isSaving = false, pendingPhotoUri = null, caption = "", isPhotoSaved = true) }
            }
            result.onFailure {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Could not save your photo, try again") }
            }
        }
    }

    //muestra u oculta el dialogo que confirma el borrado
    fun onShowDeleteDialog(show: Boolean) {
        _uiState.update { it.copy(showDeleteDialog = show) }
    }

    //borra la foto de hoy despues de que el usuario confirma; las de dias pasados no se pueden borrar
    fun onDeleteTodayPhoto() {
        val todayPhoto = _uiState.value.todayPhoto ?: return
        _uiState.update { it.copy(isSaving = true, showDeleteDialog = false, errorMessage = null) }
        viewModelScope.launch {
            photoRepository.deletePhoto(todayPhoto.id)
                .onSuccess { _uiState.update { it.copy(isSaving = false) } }
                .onFailure { _uiState.update { it.copy(isSaving = false, errorMessage = "Could not delete your photo, try again") } }
        }
    }

    fun onNavigatedToFeedback() {
        _uiState.update { it.copy(isPhotoSaved = false) }
    }

    //segundos desde que se abrio la herramienta hasta ahora (incluye el tiempo en la camara)
    fun getSessionDurationSeconds(): Int = ((now() - sessionStartMs) / 1000L).toInt().coerceAtLeast(0)

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
