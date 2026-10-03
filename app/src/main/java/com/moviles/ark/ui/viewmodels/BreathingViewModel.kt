package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.data.local.sensors.AudioPlayerHelper
import com.moviles.ark.domain.models.AmbientTrackModel
import com.moviles.ark.domain.models.BreathingSession
import com.moviles.ark.domain.models.ToolLatencyTracker
import com.moviles.ark.domain.repositories.BreathingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel managing the breathing exercise timer, phase transitions, and Jamendo ambient audio playback (#7).
 */
class BreathingViewModel(
    private val breathingRepository: BreathingRepository? = null,
    private val audioPlayerHelper: AudioPlayerHelper? = null,
    //cierra la medicion de latencia que empezo con el toque en el home (#90)
    private val toolLatencyTracker: ToolLatencyTracker? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreathingUiState())
    val uiState: StateFlow<BreathingUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var phaseElapsedMs: Long = 0L
    //tiempo que el temporizador ha corrido en esta sesion, sin contar las pausas (#82)
    private var sessionRunningMs: Long = 0L

    init {
        loadAmbientTracks()
    }

    //carga las canciones relajantes desde el repositorio de respiracion (jamendo)
    fun loadAmbientTracks() {
        if (breathingRepository == null) return

        _uiState.update { it.copy(isLoadingMusic = true) }
        viewModelScope.launch {
            val result = breathingRepository.getAmbientTracks()
            result.onSuccess { tracks ->
                _uiState.update { current ->
                    current.copy(
                        ambientTracks = tracks,
                        selectedTrack = current.selectedTrack ?: tracks.firstOrNull(),
                        isLoadingMusic = false
                    )
                }
            }
            result.onFailure {
                _uiState.update { it.copy(isLoadingMusic = false) }
            }
        }
    }

    //reproduce o pausa la musica ambiental
    fun toggleMusic() {
        val currentState = _uiState.value
        val track = currentState.selectedTrack ?: currentState.ambientTracks.firstOrNull() ?: return

        if (currentState.isMusicPlaying) {
            audioPlayerHelper?.pause()
            _uiState.update { it.copy(isMusicPlaying = false) }
        } else {
            _uiState.update { it.copy(isMusicPlaying = true, selectedTrack = track) }
            playTrack(track)
        }
    }

    //cambia de cancion seleccionada
    fun selectTrack(track: AmbientTrackModel) {
        val wasPlaying = _uiState.value.isMusicPlaying
        _uiState.update { it.copy(selectedTrack = track) }

        if (wasPlaying) {
            playTrack(track)
        }
    }

    //reproduce una pista; si falla (por ejemplo sin internet) pasa a la pista que viene en la app (#82)
    private fun playTrack(track: AmbientTrackModel) {
        audioPlayerHelper?.playUrl(
            url = track.audioUrl,
            onPlaybackStateChanged = { isPlaying ->
                _uiState.update { it.copy(isMusicPlaying = isPlaying) }
            },
            onError = { playOfflineTrackInsteadOf(track) }
        )
    }

    private fun playOfflineTrackInsteadOf(failedTrack: AmbientTrackModel) {
        val offlineTrack = _uiState.value.ambientTracks.firstOrNull { it.isAvailableOffline } ?: return
        //si la que fallo ya era la de la app no se reintenta, para no quedar en un ciclo
        if (failedTrack.isAvailableOffline) return
        _uiState.update { it.copy(selectedTrack = offlineTrack, isMusicPlaying = true) }
        playTrack(offlineTrack)
    }

    //pasa a la siguiente cancion de la lista
    fun nextTrack() {
        val tracks = _uiState.value.ambientTracks
        if (tracks.isEmpty()) return

        val currentIndex = tracks.indexOfFirst { it.id == _uiState.value.selectedTrack?.id }
        val nextIndex = if (currentIndex in 0 until tracks.size - 1) currentIndex + 1 else 0
        selectTrack(tracks[nextIndex])
    }

    //vuelve a la cancion anterior de la lista
    fun previousTrack() {
        val tracks = _uiState.value.ambientTracks
        if (tracks.isEmpty()) return

        val currentIndex = tracks.indexOfFirst { it.id == _uiState.value.selectedTrack?.id }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else tracks.size - 1
        selectTrack(tracks[prevIndex])
    }

    //muestra u oculta el selector desplegable de canciones
    fun toggleTrackSelector() {
        _uiState.update { it.copy(showTrackSelector = !it.showTrackSelector) }
    }

    fun togglePlayPause() {
        if (_uiState.value.isRunning) {
            pause()
        } else {
            start()
        }
    }

    fun start() {
        if (_uiState.value.isRunning) return

        val currentPhase = if (_uiState.value.phase == BreathingPhase.READY) {
            BreathingPhase.INHALE
        } else {
            _uiState.value.phase
        }

        _uiState.value = _uiState.value.copy(
            isRunning = true,
            phase = currentPhase
        )

        startTimerLoop()
    }

    fun pause() {
        timerJob?.cancel()
        timerJob = null
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    fun reset() {
        //reiniciar cierra la sesion actual: si termino algun ciclo, se guarda antes de borrar el contador
        finishSession()
        phaseElapsedMs = 0L
        _uiState.value = _uiState.value.copy(
            phase = BreathingPhase.READY,
            completedCycles = 0,
            secondsRemainingInPhase = _uiState.value.inhaleDuration,
            phaseProgress = 0f
        )
    }

    fun updateDurations(inhale: Int, hold: Int, exhale: Int) {
        val wasRunning = _uiState.value.isRunning
        pause()
        phaseElapsedMs = 0L
        _uiState.value = _uiState.value.copy(
            inhaleDuration = inhale.coerceAtLeast(1),
            holdDuration = hold.coerceAtLeast(1),
            exhaleDuration = exhale.coerceAtLeast(1),
            phase = BreathingPhase.READY,
            secondsRemainingInPhase = inhale,
            phaseProgress = 0f
        )
        if (wasRunning) start()
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.IO) {
            val tickIntervalMs = 50L

            while (_uiState.value.isRunning) {
                delay(tickIntervalMs)
                phaseElapsedMs += tickIntervalMs
                sessionRunningMs += tickIntervalMs

                val currentPhase = _uiState.value.phase
                val targetDurationSeconds = getPhaseDuration(currentPhase)
                val targetDurationMs = targetDurationSeconds * 1000L

                val progress = (phaseElapsedMs.toFloat() / targetDurationMs).coerceIn(0f, 1f)
                val remainingMs = (targetDurationMs - phaseElapsedMs).coerceAtLeast(0L)
                val secondsRemaining = ((remainingMs + 999L) / 1000L).toInt()

                if (phaseElapsedMs >= targetDurationMs) {
                    advanceToNextPhase()
                } else {
                    _uiState.value = _uiState.value.copy(
                        phaseProgress = progress,
                        secondsRemainingInPhase = secondsRemaining
                    )
                }
            }
        }
    }

    private fun advanceToNextPhase() {
        phaseElapsedMs = 0L
        val currentPhase = _uiState.value.phase

        val (nextPhase, isCycleCompleted) = when (currentPhase) {
            BreathingPhase.READY -> Pair(BreathingPhase.INHALE, false)
            BreathingPhase.INHALE -> Pair(BreathingPhase.HOLD, false)
            BreathingPhase.HOLD -> Pair(BreathingPhase.EXHALE, false)
            BreathingPhase.EXHALE -> Pair(BreathingPhase.INHALE, true)
        }

        val updatedCycles = if (isCycleCompleted) {
            _uiState.value.completedCycles + 1
        } else {
            _uiState.value.completedCycles
        }

        val nextDuration = getPhaseDuration(nextPhase)

        _uiState.value = _uiState.value.copy(
            phase = nextPhase,
            completedCycles = updatedCycles,
            secondsRemainingInPhase = nextDuration,
            phaseProgress = 0f
        )
    }

    //la pantalla termino su primer dibujo: se cierra la medicion de latencia que empezo con el toque (#90)
    fun onScreenRendered() {
        toolLatencyTracker?.onToolRendered(BreathingSession.TOOL_ID)
    }

    //el usuario sale de la herramienta o reinicia: si termino al menos un ciclo, se guarda la sesion en el telefono (#82)
    fun finishSession(): Boolean {
        pause()
        val state = _uiState.value
        val session = BreathingSession(
            completedCycles = state.completedCycles,
            durationSeconds = (sessionRunningMs / 1000L).toInt(),
            timestamp = System.currentTimeMillis(),
            pattern = BreathingSession.patternOf(state.inhaleDuration, state.holdDuration, state.exhaleDuration)
        )
        //se reinicia el tiempo para que la misma sesion no se guarde dos veces
        sessionRunningMs = 0L
        //le preguntamos al modelo si la sesion cuenta antes de guardarla
        val isComplete = session.isComplete()
        if (!isComplete) return false
        val repository = breathingRepository ?: return true
        viewModelScope.launch {
            //NonCancellable: al salir de la pantalla el viewmodel se limpia enseguida y no debe cortar el guardado
            withContext(NonCancellable) {
                repository.saveSession(session)
            }
        }
        return true
    }

    private fun getPhaseDuration(phase: BreathingPhase): Int {
        return when (phase) {
            BreathingPhase.READY -> _uiState.value.inhaleDuration
            BreathingPhase.INHALE -> _uiState.value.inhaleDuration
            BreathingPhase.HOLD -> _uiState.value.holdDuration
            BreathingPhase.EXHALE -> _uiState.value.exhaleDuration
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        audioPlayerHelper?.release()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                BreathingViewModel(
                    breathingRepository = app.container.breathingRepository,
                    audioPlayerHelper = app.container.audioPlayerHelper,
                    toolLatencyTracker = app.container.toolLatencyTracker
                )
            }
        }
    }
}
