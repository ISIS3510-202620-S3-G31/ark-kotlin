package com.moviles.ark.ui.viewmodels

import android.os.SystemClock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.adapters.AmbientAudioPlayer
import com.moviles.ark.domain.adapters.HapticFeedbackAdapter
import com.moviles.ark.domain.models.BreathingPattern
import com.moviles.ark.domain.models.BreathingPhase
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
 * ViewModel managing the breathing exercise timer, phase transitions, and Jamendo ambient audio playback.
 */
class BreathingViewModel(
    private val breathingRepository: BreathingRepository? = null,
    private val audioPlayerHelper: AmbientAudioPlayer? = null,
    //vibracion al pasar de hold a exhale; null en previews
    private val hapticFeedback: HapticFeedbackAdapter? = null,
    //cierra la medicion de latencia que empezo con el toque en el home
    private val toolLatencyTracker: ToolLatencyTracker? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreathingUiState())
    val uiState: StateFlow<BreathingUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var phaseElapsedMs: Long = 0L
    //tiempo que el temporizador ha corrido en esta sesion, sin contar las pausas
    private var sessionRunningMs: Long = 0L
    private var phaseStartRealtime: Long = 0L
    private var sessionStartRealtime: Long = 0L

    //carga de la pista elegida y descarga para offline en curso; se cancelan al cambiar de pista
    private var playbackJob: Job? = null
    private var offlineCacheJob: Job? = null
    //pista que tiene cargada el reproductor, para reanudarla desde donde iba
    private var loadedTrackId: String? = null

    init {
        //la pista de la app es la unica predeterminada: suena de una vez, con o sin internet
        breathingRepository?.defaultTrack?.let { track ->
            _uiState.update { it.copy(ambientTracks = listOf(track), defaultTrackId = track.id, selectedTrack = track) }
        }
        loadAmbientTracks()
    }

    //carga las canciones relajantes desde el repositorio de respiracion (jamendo o, sin conexion, la cache)
    fun loadAmbientTracks() {
        val repository = breathingRepository ?: return
        if (_uiState.value.isLoadingTracks) return

        _uiState.update { it.copy(isLoadingTracks = true, tracksLoadFailed = false) }
        viewModelScope.launch {
            val catalog = repository.getAmbientTracks()
            _uiState.update { current ->
                current.copy(
                    ambientTracks = catalog.tracks,
                    //la pista elegida se conserva aunque cambie la lista; si no habia, la primera (la de la app)
                    selectedTrack = catalog.tracks.firstOrNull { it.id == current.selectedTrack?.id }
                        ?: current.selectedTrack
                        ?: catalog.tracks.firstOrNull(),
                    isLoadingTracks = false,
                    tracksLoadFailed = !catalog.isOnline
                )
            }
        }
    }

    //boton de play/pausa de la musica: pausa la que suena o reproduce la elegida
    fun toggleMusic() {
        val state = _uiState.value
        val track = state.selectedTrack ?: state.ambientTracks.firstOrNull() ?: return
        if (state.isMusicActive) pauseMusic() else playTrack(track)
    }

    //toque sobre una pista de la hoja: la elegida se pausa o reanuda; otra pista se cambia y empieza a sonar
    fun onTrackTapped(track: AmbientTrackModel) {
        if (track.id == _uiState.value.selectedTrack?.id) toggleMusic() else playTrack(track)
    }

    private fun pauseMusic() {
        //si todavia estaba cargando se cancela para que no empiece a sonar despues de la pausa
        playbackJob?.cancel()
        audioPlayerHelper?.pause()
        _uiState.update { it.copy(isMusicPlaying = false, isTrackBuffering = false) }
    }

    //reproduce la pista en bucle hasta que el usuario la pause, elija otra o salga de la pantalla
    private fun playTrack(track: AmbientTrackModel) {
        val audioPlayer = audioPlayerHelper ?: return
        playbackJob?.cancel()
        if (track.id != loadedTrackId) {
            //la pista anterior se calla de una vez para que no siga avisando que suena mientras carga la nueva
            audioPlayer.pause()
        }
        //solo se descarga para offline la pista que se esta escuchando
        if (track.id != _uiState.value.selectedTrack?.id) offlineCacheJob?.cancel()
        _uiState.update {
            it.copy(selectedTrack = track, isMusicPlaying = false, isTrackBuffering = true, unplayableTrackId = null)
        }

        //la misma pista que estaba en pausa sigue desde donde iba
        if (track.id == loadedTrackId && audioPlayer.resume()) return

        playbackJob = viewModelScope.launch {
            val audioUrl = breathingRepository?.resolveTrackAudio(track) ?: track.audioUrl
            loadedTrackId = track.id
            audioPlayer.playUrl(
                url = audioUrl,
                onPlaybackStateChanged = { isPlaying ->
                    _uiState.update { it.copy(isMusicPlaying = isPlaying, isTrackBuffering = false) }
                },
                onError = { onTrackPlaybackFailed(track) }
            )
            //mientras suena por streaming se guarda en la cache del disco para que quede disponible sin internet
            if (!track.isAvailableOffline) saveTrackForOffline(track)
        }
    }

    private fun saveTrackForOffline(track: AmbientTrackModel) {
        val repository = breathingRepository ?: return
        if (offlineCacheJob?.isActive == true) return
        offlineCacheJob = viewModelScope.launch {
            val savedTrack = repository.saveTrackForOffline(track) ?: return@launch
            //la pista ya suena sin internet: se marca en la lista
            _uiState.update { current ->
                current.copy(
                    ambientTracks = current.ambientTracks.map { if (it.id == savedTrack.id) it.copy(isAvailableOffline = true) else it },
                    selectedTrack = current.selectedTrack?.let { if (it.id == savedTrack.id) it.copy(isAvailableOffline = true) else it }
                )
            }
        }
    }

    //no se pudo reproducir (por ejemplo se perdio la conexion en pleno streaming): se avisa en su fila y no se cambia sola
    private fun onTrackPlaybackFailed(track: AmbientTrackModel) {
        loadedTrackId = null
        _uiState.update { it.copy(isMusicPlaying = false, isTrackBuffering = false, unplayableTrackId = track.id) }
    }

    //abre la hoja con las pistas; si la ultima carga fallo (por ejemplo se abrio sin internet) se vuelve a intentar
    fun openTrackSelector() {
        _uiState.update { it.copy(showTrackSelector = true) }
        if (_uiState.value.tracksLoadFailed) loadAmbientTracks()
    }

    fun dismissTrackSelector() {
        _uiState.update { it.copy(showTrackSelector = false) }
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

        val isFirstPhase = _uiState.value.phase == BreathingPhase.READY
        val currentPhase = if (isFirstPhase) {
            BreathingPhase.INHALE
        } else {
            _uiState.value.phase
        }
        //la primera inhalacion tambien vibra; al reanudar una pausa la etapa sigue y no se repite
        if (isFirstPhase) hapticFeedback?.vibratePhaseCue(BreathingPhase.INHALE)

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

    //reiniciar vuelve el circulo al inicio dentro de la misma sesion: no la termina ni la abandona
    fun reset() {
        pause()
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
                isCustomSelected = false,
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
        //el modelo normaliza el ritmo y dice si coincide con un preset
        val pattern = BreathingPattern(inhale, hold, exhale, holdPostExhale).normalized()
        val matchingPreset = pattern.matchingPreset() ?: _uiState.value.selectedPreset

        _uiState.update { current ->
            current.copy(
                selectedPreset = matchingPreset,
                inhaleDuration = pattern.inhale,
                holdDuration = pattern.hold,
                exhaleDuration = pattern.exhale,
                holdPostExhaleDuration = pattern.holdPostExhale,
                phase = BreathingPhase.READY,
                secondsRemainingInPhase = pattern.inhale,
                phaseProgress = 0f
            )
        }
        if (wasRunning) start()
    }

    fun updateDurations(inhale: Int, hold: Int, exhale: Int) {
        updateDurations(inhale, hold, exhale, 0)
    }

    //opcion Custom del selector: usa el ritmo guardado y, si nunca se configuro, abre la hoja para hacerlo
    fun selectCustom() {
        val state = _uiState.value
        updateDurations(state.customInhale, state.customHold, state.customExhale)
        _uiState.update { it.copy(isCustomSelected = true, showCustomRhythmSheet = !it.isCustomConfigured) }
    }

    fun openCustomRhythmSheet() {
        _uiState.update { it.copy(showCustomRhythmSheet = true) }
    }

    fun dismissCustomRhythmSheet() {
        _uiState.update { it.copy(showCustomRhythmSheet = false) }
    }

    //boton Done de la hoja: guarda el ritmo (solo mientras la pantalla siga abierta) y lo aplica al circulo
    fun saveCustomRhythm(inhale: Int, hold: Int, exhale: Int) {
        //el modelo deja el ritmo Custom dentro de los rangos permitidos
        val custom = BreathingPattern.custom(inhale, hold, exhale)
        updateDurations(custom.inhale, custom.hold, custom.exhale)
        _uiState.update {
            it.copy(
                isCustomSelected = true,
                customInhale = custom.inhale,
                customHold = custom.hold,
                customExhale = custom.exhale,
                isCustomConfigured = true,
                showCustomRhythmSheet = false
            )
        }
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

        //el modelo dice que fase sigue y si se completo un ciclo
        val (nextPhase, isCycleCompleted) = _uiState.value.pattern.next(currentPhase)
        //cada etapa que empieza vibra, tambien en cada ciclo nuevo
        hapticFeedback?.vibratePhaseCue(nextPhase)

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

    //la pantalla termino su primer dibujo: se cierra la medicion de latencia que empezo con el toque
    fun onScreenRendered() {
        toolLatencyTracker?.onToolRendered(BreathingSession.TOOL_ID)
    }

    //boton Finish: la sesion queda terminada; devuelve si cuenta como terminada y cuantos segundos duro
    fun finishSession(): Pair<Boolean, Int> = endSession(finishedByUser = true)

    //atras (boton o gesto): el usuario abandona la herramienta; devuelve cuantos segundos alcanzo a respirar
    fun abandonSession(): Int = endSession(finishedByUser = false).second

    //guarda la sesion en el telefono como terminada o abandonada; la interaccion lleva complete = true / false
    private fun endSession(finishedByUser: Boolean): Pair<Boolean, Int> {
        pause()
        val state = _uiState.value
        val durationSeconds = (sessionRunningMs / 1000L).toInt()
        val session = BreathingSession(
            completedCycles = state.completedCycles,
            durationSeconds = durationSeconds,
            timestamp = System.currentTimeMillis(),
            pattern = BreathingSession.patternOf(state.inhaleDuration, state.holdDuration, state.exhaleDuration, state.holdPostExhaleDuration),
            finishedByUser = finishedByUser
        )
        //se reinicia el tiempo para que la misma sesion no se guarde dos veces
        sessionRunningMs = 0L
        //si el temporizador nunca corrio no hubo sesion: abrir y cerrar la pantalla no cuenta como abandono
        val repository = breathingRepository
        if (durationSeconds > 0 && repository != null) {
            viewModelScope.launch {
                //NonCancellable: al salir de la pantalla el viewmodel se limpia enseguida y no debe cortar el guardado
                withContext(NonCancellable) {
                    repository.saveSession(session)
                }
            }
        }
        return Pair(session.isComplete(), durationSeconds)
    }

    private fun getPhaseDuration(phase: BreathingPhase): Int = _uiState.value.pattern.durationOf(phase)

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
                    hapticFeedback = app.container.hapticFeedback,
                    toolLatencyTracker = app.container.toolLatencyTracker
                )
            }
        }
    }
}


