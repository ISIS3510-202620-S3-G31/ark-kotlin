package com.moviles.ark.ui.viewmodels

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.adapters.CookieImageLoader
import com.moviles.ark.domain.exceptions.InvalidCookieImageException
import com.moviles.ark.domain.exceptions.NotEnoughSpaceException
import com.moviles.ark.domain.models.CookieMessages
import com.moviles.ark.domain.models.CookieModel
import com.moviles.ark.domain.repositories.CookieRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//lo que pasa una sola vez en el formulario: se guardo (y se vuelve al jarro) o un aviso
sealed interface NewCookieEvent {
    data class Saved(val typedTitle: String, val cookie: CookieModel) : NewCookieEvent
    data class Message(val text: String) : NewCookieEvent
}

//formulario de galleta nueva: titulo (60), texto (280), dictado e imagen opcional
//los limites y el titulo final con sufijo los decide el modelo; aqui solo se orquesta
class NewCookieViewModel(
    private val cookieRepository: CookieRepository,
    private val imageLoader: CookieImageLoader<Bitmap>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewCookieUiState())
    val uiState: StateFlow<NewCookieUiState> = _uiState.asStateFlow()

    private val events = Channel<NewCookieEvent>(Channel.BUFFERED)
    val eventFlow: Flow<NewCookieEvent> = events.receiveAsFlow()

    private var titleJob: Job? = null
    private var previewJob: Job? = null
    //foto temporal de la camara; se borra al guardar o al salir porque la galleta guarda su propia copia
    private var cameraFile: String? = null

    fun onTitleChange(title: String) {
        if (title.length > CookieModel.MAX_TITLE_LENGTH) return
        _uiState.update { it.copy(title = title, titleError = null) }
        //se muestra antes de guardar el titulo final si ya existe uno igual
        titleJob?.cancel()
        titleJob = viewModelScope.launch {
            delay(TITLE_CHECK_DELAY_MS)
            val clean = CookieModel.cleanTitle(title)
            val finalTitle = if (clean.isEmpty()) null else cookieRepository.resolveTitle(clean)
            _uiState.update { it.copy(finalTitle = finalTitle?.takeIf { final -> final != clean }) }
        }
    }

    fun onTextChange(text: String) {
        if (text.length > CookieModel.MAX_TEXT_LENGTH) return
        _uiState.update { it.copy(text = text, textError = null) }
    }

    fun hasCameraPermission(): Boolean = cookieRepository.hasCameraPermission()

    //prepara el archivo de la camara y devuelve su direccion para abrirla; null si no se pudo crear
    fun prepareCapture(): String? {
        val uri = cookieRepository.createCaptureUri()
        _uiState.update { it.copy(captureUri = uri) }
        if (uri == null) events.trySend(NewCookieEvent.Message(CAMERA_ERROR))
        return uri
    }

    fun onCaptureResult(success: Boolean) {
        val uri = _uiState.value.captureUri ?: return
        _uiState.update { it.copy(captureUri = null) }
        if (success) {
            discardCameraFile()
            cameraFile = uri
            onImageSelected(uri)
        } else {
            viewModelScope.launch { cookieRepository.discardCapture(uri) }
        }
    }

    //imagen de la camara o de la galeria (photo picker)
    fun onImageSelected(uri: String) {
        if (uri != cameraFile) discardCameraFile()
        _uiState.update { it.copy(imageUri = uri, imagePreview = null) }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val preview = imageLoader.loadPreview(uri)
            if (preview == null) {
                _uiState.update { it.copy(imageUri = null) }
                events.send(NewCookieEvent.Message(InvalidCookieImageException.MESSAGE))
            } else {
                _uiState.update { state -> if (state.imageUri == uri) state.copy(imagePreview = preview) else state }
            }
        }
    }

    fun onRemoveImage() {
        previewJob?.cancel()
        discardCameraFile()
        _uiState.update { it.copy(imageUri = null, imagePreview = null) }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving) return
        val titleError = CookieModel.titleError(state.title)
        val textError = CookieModel.textError(state.text)
        if (titleError != null || textError != null) {
            _uiState.update { it.copy(titleError = titleError, textError = textError) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            cookieRepository.createCookie(state.title, state.text, state.imageUri)
                .onSuccess { cookie ->
                    discardCameraFile()
                    _uiState.update { it.copy(isSaving = false) }
                    events.send(NewCookieEvent.Saved(state.title, cookie))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false) }
                    val text = when (error) {
                        is NotEnoughSpaceException -> CookieMessages.NOT_ENOUGH_SPACE
                        is InvalidCookieImageException -> InvalidCookieImageException.MESSAGE
                        else -> SAVE_ERROR
                    }
                    events.send(NewCookieEvent.Message(text))
                }
        }
    }

    private fun discardCameraFile() {
        val file = cameraFile ?: return
        cameraFile = null
        viewModelScope.launch { withContext(NonCancellable) { cookieRepository.discardCapture(file) } }
    }

    override fun onCleared() {
        //si se sale sin guardar, la foto temporal de la camara no se queda ocupando espacio
        cameraFile?.let { file ->
            cameraFile = null
            @Suppress("OPT_IN_USAGE")
            kotlinx.coroutines.GlobalScope.launch { cookieRepository.discardCapture(file) }
        }
        super.onCleared()
    }

    companion object {
        private const val TITLE_CHECK_DELAY_MS = 250L
        const val SAVE_ERROR = "Couldn't save your cookie. Try again"
        const val CAMERA_ERROR = "Could not open the camera, try again"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                NewCookieViewModel(
                    cookieRepository = app.container.cookieRepository,
                    imageLoader = app.container.cookieImageLoader,
                )
            }
        }
    }
}
