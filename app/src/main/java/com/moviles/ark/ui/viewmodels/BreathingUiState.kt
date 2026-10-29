package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.AmbientTrackModel
import com.moviles.ark.domain.models.BreathingPattern
import com.moviles.ark.domain.models.BreathingSession
import com.moviles.ark.domain.models.BreathingPhase
import com.moviles.ark.domain.models.BreathingPreset

/**
 * UI State for Custom Breathing tool exercise with Jamendo ambient music integration
 * and medically verified guided breathing presets
 */
data class BreathingUiState(
    val phase: BreathingPhase = BreathingPhase.READY,
    val isRunning: Boolean = false,
    val completedCycles: Int = 0,
    val secondsRemainingInPhase: Int = 4,
    val selectedPreset: BreathingPreset = BreathingPreset.BOX,
    val inhaleDuration: Int = 4,
    val holdDuration: Int = 4,
    val exhaleDuration: Int = 4,
    val holdPostExhaleDuration: Int = 4,
    val phaseProgress: Float = 0f, // 0.0f to 1.0f progress within current phase
    //ritmo propio del usuario (opcion Custom del selector); arranca con los valores del diseno 5-2-6
    val isCustomSelected: Boolean = false,
    val customInhale: Int = 5,
    val customHold: Int = 2,
    val customExhale: Int = 6,
    //false hasta que el usuario guarda su ritmo la primera vez; mientras tanto elegir Custom abre la hoja sola
    val isCustomConfigured: Boolean = false,
    val showCustomRhythmSheet: Boolean = false,
    // Estado de musica ambiental
    val ambientTracks: List<AmbientTrackModel> = emptyList(),
    val selectedTrack: AmbientTrackModel? = null,
    val isMusicPlaying: Boolean = false,
    val isLoadingMusic: Boolean = false,
    val showTrackSelector: Boolean = false
) {
    //ritmo actual como modelo del dominio
    val pattern: BreathingPattern
        get() = BreathingPattern(inhaleDuration, holdDuration, exhaleDuration, holdPostExhaleDuration)

    //la regla de cuando se puede reiniciar vive en el modelo
    val canReset: Boolean
        get() = BreathingPattern.canReset(phase, completedCycles)

    //la regla de cuando se puede terminar tambien
    val canFinish: Boolean
        get() = BreathingSession.canFinish(completedCycles)
}
