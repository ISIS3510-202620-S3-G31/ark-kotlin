package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing the breathing exercise timer, phase transitions, and cycle counts.
 * State survives configuration changes (like screen rotation) because it is tied to viewModelScope.
 */
class BreathingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BreathingUiState())
    val uiState: StateFlow<BreathingUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var phaseElapsedMs: Long = 0L

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
        pause()
        phaseElapsedMs = 0L
        _uiState.value = BreathingUiState(
            inhaleDuration = _uiState.value.inhaleDuration,
            holdDuration = _uiState.value.holdDuration,
            exhaleDuration = _uiState.value.exhaleDuration
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
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BreathingViewModel()
            }
        }
    }
}
