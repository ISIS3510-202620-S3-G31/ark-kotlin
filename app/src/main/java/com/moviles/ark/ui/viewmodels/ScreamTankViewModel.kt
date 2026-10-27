package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.models.DecibelMeter
import com.moviles.ark.domain.models.ScreamSession
import com.moviles.ark.domain.models.ToolLatencyTracker
import com.moviles.ark.domain.repositories.ScreamTankRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

//viewmodel del scream tank: medidor en tiempo real, permiso del microfono y guardado de la sesion
class ScreamTankViewModel(
    private val screamTankRepository: ScreamTankRepository? = null,
    //cierra la medicion de latencia que empezo con el toque en el home
    private val toolLatencyTracker: ToolLatencyTracker? = null,
    //reloj monotono en nanosegundos; se puede cambiar para fijar el tiempo
    private val nanoTime: () -> Long = { System.nanoTime() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScreamTankUiState())
    val uiState: StateFlow<ScreamTankUiState> = _uiState.asStateFlow()

    private var listenJob: Job? = null
    //tiempo escuchado en las partes ya pausadas y cuando empezo la parte actual (null si no esta escuchando)
    private var listenedNanos: Long = 0L
    private var listenStartNanos: Long? = null

    init {
        //si falta el permiso se explica de una vez para que se pide el microfono
        if (screamTankRepository != null) {
            if (screamTankRepository.hasMicrophonePermission()) {
                _uiState.update { it.copy(permission = MicPermissionState.GRANTED) }
            } else {
                _uiState.update { it.copy(showPermissionDialog = true) }
            }
        }
    }

    //la pantalla termino su primer dibujo: se cierra la medicion de latencia que empezo con el toque
    fun onScreenRendered() {
        toolLatencyTracker?.onToolRendered(ScreamSession.TOOL_ID)
    }

    //al volver a la pantalla (por ejemplo desde ajustes) se revisa si el permiso cambio
    fun refreshPermission() {
        val repository = screamTankRepository ?: return
        if (repository.hasMicrophonePermission()) {
            _uiState.update { it.copy(permission = MicPermissionState.GRANTED, showPermissionDialog = false) }
        } else if (_uiState.value.permission == MicPermissionState.GRANTED) {
            pauseListening()
            _uiState.update { it.copy(permission = MicPermissionState.DENIED) }
        }
    }

    //respuesta del dialogo del sistema; canAskAgain = false si marco "no volver a preguntar"
    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        _uiState.update {
            when {
                granted -> it.copy(permission = MicPermissionState.GRANTED, showPermissionDialog = false)
                canAskAgain -> it.copy(permission = MicPermissionState.DENIED, showPermissionDialog = false)
                //bloqueado: se deja el dialogo abierto para explicar como activarlo en ajustes
                else -> it.copy(permission = MicPermissionState.PERMANENTLY_DENIED, showPermissionDialog = true)
            }
        }
    }

    fun showPermissionDialog() {
        _uiState.update { it.copy(showPermissionDialog = true) }
    }

    fun dismissPermissionDialog() {
        _uiState.update { it.copy(showPermissionDialog = false) }
    }

    fun toggleListening() {
        if (_uiState.value.isListening) {
            pauseListening()
        } else if (_uiState.value.permission != MicPermissionState.GRANTED) {
            showPermissionDialog()
        } else {
            startListening()
        }
    }

    private fun startListening() {
        val repository = screamTankRepository ?: return
        if (listenJob?.isActive == true) return

        listenStartNanos = nanoTime()
        _uiState.update { it.copy(isListening = true, hasStarted = true, errorMessage = null) }
        listenJob = viewModelScope.launch {
            repository.microphoneLevels()
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            errorMessage = "The microphone is not available right now. Try again in a moment.",
                            permission = if (e is SecurityException) MicPermissionState.DENIED else it.permission
                        )
                    }
                }
                .collect { reading ->
                    _uiState.update {
                        it.copy(
                            currentLevel = reading.level,
                            currentDbfs = reading.dbfs,
                            peakLevel = max(it.peakLevel, reading.level),
                            elapsedSeconds = listenedSeconds()
                        )
                    }
                }
            //solo se llega aqui si el microfono fallo; al pausar se cancela antes
            stopClock()
            listenJob = null
            _uiState.update { it.copy(isListening = false, currentLevel = 0, currentDbfs = DecibelMeter.MIN_DBFS) }
        }
    }

    //suelta el microfono pero conserva el pico y el tiempo de la sesion
    fun pauseListening() {
        val job = listenJob ?: return
        job.cancel()
        listenJob = null
        stopClock()
        _uiState.update {
            it.copy(
                isListening = false,
                currentLevel = 0,
                currentDbfs = DecibelMeter.MIN_DBFS,
                elapsedSeconds = listenedSeconds()
            )
        }
    }

    //boton de reiniciar: suelta el microfono y empieza de cero sin guardar lo anterior
    fun restartSession() {
        pauseListening()
        listenedNanos = 0L
        _uiState.update {
            it.copy(
                hasStarted = false,
                peakLevel = 0,
                elapsedSeconds = 0,
                currentLevel = 0,
                currentDbfs = DecibelMeter.MIN_DBFS,
                errorMessage = null
            )
        }
    }

    //el usuario termina o sale de la herramienta: si el microfono alcanzo a escuchar, se guarda la sesion
    fun finishSession(): Boolean {
        val (completed, _) = finishSessionWithDuration()
        return completed
    }

    fun finishSessionWithDuration(): Pair<Boolean, Int> {
        pauseListening()
        val durationSeconds = listenedSeconds()
        val session = ScreamSession(
            peakLevel = _uiState.value.peakLevel,
            durationSeconds = durationSeconds,
            timestamp = System.currentTimeMillis()
        )
        //se reinicia el tiempo para que la misma sesion no se guarde dos veces
        listenedNanos = 0L
        //le preguntamos al modelo si la sesion cuenta antes de guardarla
        if (!session.isComplete()) return Pair(false, durationSeconds)
        val repository = screamTankRepository ?: return Pair(true, durationSeconds)
        viewModelScope.launch {
            //NonCancellable: al salir de la pantalla el viewmodel se limpia enseguida y no debe cortar el guardado
            withContext(NonCancellable) {
                repository.saveSession(session)
            }
        }
        return Pair(true, durationSeconds)
    }

    private fun stopClock() {
        val start = listenStartNanos ?: return
        listenedNanos += nanoTime() - start
        listenStartNanos = null
    }

    private fun listenedSeconds(): Int {
        val running = listenStartNanos?.let { nanoTime() - it } ?: 0L
        return ((listenedNanos + running) / 1_000_000_000L).toInt()
    }

    override fun onCleared() {
        super.onCleared()
        listenJob?.cancel()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                ScreamTankViewModel(
                    screamTankRepository = app.container.screamTankRepository,
                    toolLatencyTracker = app.container.toolLatencyTracker
                )
            }
        }
    }
}
