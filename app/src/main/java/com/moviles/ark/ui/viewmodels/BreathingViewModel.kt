package com.moviles.ark.ui.viewmodels

import android.os.SystemClock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.data.local.sensors.AudioPlayerHelper
import com.moviles.ark.domain.models.AmbientTrackModel
import com.moviles.ark.domain.models.BreathingPreset
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
 * ViewModel managing the breathing exercise timer, phase transitions, and Jamendo ambient audio playback .
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
    private var phaseStartRealtime: Long = 0L
    private var sessionStartRealtime: Long = 0L

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

    //reproduce una pista; si falla (por ejemplo sin internet) pasa a la pista que viene en la app )
    private fun playTrack(track: AmbientTrackModel) {
        viewModelScope.launch {
            val audioUrl = breathingRepository?.resolveTrackAudio(track) ?: track.audioUrl
            audioPlayerHelper?.playUrl(
            url = audioUrl,
            onPlaybackStateChanged = { isPlaying ->
                _uiState.update { it.copy(isMusicPlaying = isPlaying) }
            },
            onError = { playOfflineTrackInsteadOf(track) }
            )
        }
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

        val now = SystemClock.elapsedRealtime()
        phaseStartRealtime = now - phaseElapsedMs
        sessionStartRealtime = now - sessionRunningMs

        _uiState.value = _uiState.value.copy(
            isRunning = true,
            phase = currentPhase
        )

        startTimerLoop()
    }

    fun pause() {
        timerJob?.cancel()
        timerJob = null
        if (_uiState.value.isRunning) {
            val now = SystemClock.elapsedRealtime()
            phaseElapsedMs = (now - phaseStartRealtime).coerceAtLeast(0L)
            sessionRunningMs = (now - sessionStartRealtime).coerceAtLeast(0L)
        }
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

    fun selectPreset(preset: BreathingPreset) {
        val wasRunning = _uiState.value.isRunning
        pause()
        phaseElapsedMs = 0L
        _uiState.update { current ->
            current.copy(
                selectedPreset = preset,
                inhaleDuration = preset.inhale,
                holdDuration = preset.hold,
                exhaleDuration = preset.exhale,
                holdPostExhaleDuration = preset.holdPostExhale,
                phase = BreathingPhase.READY,
                secondsRemainingInPhase = preset.inhale,
                phaseProgress = 0f
            )
        }
        if (wasRunning) start()
    }

    fun updateDurations(inhale: Int, hold: Int, exhale: Int, holdPostExhale: Int = 0) {
        val wasRunning = _uiState.value.isRunning
        pause()
        phaseElapsedMs = 0L
        val matchingPreset = BreathingPreset.entries.find {
            it.inhale == inhale && it.hold == hold && it.exhale == exhale && it.holdPostExhale == holdPostExhale
        } ?: _uiState.value.selectedPreset

        _uiState.update { current ->
            current.copy(
                selectedPreset = matchingPreset,
                inhaleDuration = inhale.coerceAtLeast(1),
                holdDuration = hold.coerceAtLeast(0),
                exhaleDuration = exhale.coerceAtLeast(1),
                holdPostExhaleDuration = holdPostExhale.coerceAtLeast(0),
                phase = BreathingPhase.READY,
                secondsRemainingInPhase = inhale,
                phaseProgress = 0f
            )
        }
        if (wasRunning) start()
    }

    fun updateDurations(inhale: Int, hold: Int, exhale: Int) {
        updateDurations(inhale, hold, exhale, 0)
    }

    //bucle del temporizador de respiracion en hilo principal midiendo tiempo real (elapsedRealtime) sin acumulacion de latencia
    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Main.immediate) {
            val tickIntervalMs = 16L

            while (_uiState.value.isRunning) {
                delay(tickIntervalMs)
                val now = SystemClock.elapsedRealtime()
                phaseElapsedMs = (now - phaseStartRealtime).coerceAtLeast(0L)
                sessionRunningMs = (now - sessionStartRealtime).coerceAtLeast(0L)

                val currentPhase = _uiState.value.phase
                val targetDurationSeconds = getPhaseDuration(currentPhase)
                val targetDurationMs = targetDurationSeconds * 1000L

                val progress = (phaseElapsedMs.toFloat() / targetDurationMs).coerceIn(0f, 1f)
                val remainingMs = (targetDurationMs - phaseElapsedMs).coerceAtLeast(0L)
                val secondsRemaining = ((remainingMs + 999L) / 1000L).toInt()

                if (phaseElapsedMs >= targetDurationMs) {
                    val overshoot = phaseElapsedMs - targetDurationMs
                    advanceToNextPhase(now - overshoot)
                } else {
                    _uiState.value = _uiState.value.copy(
                        phaseProgress = progress,
                        secondsRemainingInPhase = secondsRemaining
                    )
                }
            }
        }
    }

    private fun advanceToNextPhase(phaseStartTime: Long = SystemClock.elapsedRealtime()) {
        phaseStartRealtime = phaseStartTime
        phaseElapsedMs = (SystemClock.elapsedRealtime() - phaseStartRealtime).coerceAtLeast(0L)
        val currentPhase = _uiState.value.phase

        val state = _uiState.value
        val (nextPhase, isCycleCompleted) = when (currentPhase) {
            BreathingPhase.READY -> Pair(BreathingPhase.INHALE, false)
            BreathingPhase.INHALE -> {
                if (state.holdDuration > 0) {
                    Pair(BreathingPhase.HOLD, false)
                } else {
                    Pair(BreathingPhase.EXHALE, false)
                }
            }
            BreathingPhase.HOLD -> Pair(BreathingPhase.EXHALE, false)
            BreathingPhase.EXHALE -> {
                if (state.holdPostExhaleDuration > 0) {
                    Pair(BreathingPhase.HOLD_POST_EXHALE, false)
                } else {
                    Pair(BreathingPhase.INHALE, true)
                }
            }
            BreathingPhase.HOLD_POST_EXHALE -> Pair(BreathingPhase.INHALE, true)
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
        val (completed, _) = finishSessionWithDuration()
        return completed
    }

    fun finishSessionWithDuration(): Pair<Boolean, Int> {
        pause()
        val state = _uiState.value
        val durationSeconds = (sessionRunningMs / 1000L).toInt()
        val session = BreathingSession(
            completedCycles = state.completedCycles,
            durationSeconds = durationSeconds,
            timestamp = System.currentTimeMillis(),
            pattern = BreathingSession.patternOf(state.inhaleDuration, state.holdDuration, state.exhaleDuration, state.holdPostExhaleDuration)
        )
        //se reinicia el tiempo para que la misma sesion no se guarde dos veces
        sessionRunningMs = 0L
        //le preguntamos al modelo si la sesion cuenta antes de guardarla
        val isComplete = session.isComplete()
        if (!isComplete) return Pair(false, durationSeconds)
        val repository = breathingRepository ?: return Pair(true, durationSeconds)
        viewModelScope.launch {
            //NonCancellable: al salir de la pantalla el viewmodel se limpia enseguida y no debe cortar el guardado
            withContext(NonCancellable) {
                repository.saveSession(session)
            }
        }
        return Pair(true, durationSeconds)
    }

    private fun getPhaseDuration(phase: BreathingPhase): Int {
        return when (phase) {
            BreathingPhase.READY -> _uiState.value.inhaleDuration
            BreathingPhase.INHALE -> _uiState.value.inhaleDuration
            BreathingPhase.HOLD -> _uiState.value.holdDuration
            BreathingPhase.EXHALE -> _uiState.value.exhaleDuration
            BreathingPhase.HOLD_POST_EXHALE -> _uiState.value.holdPostExhaleDuration
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


