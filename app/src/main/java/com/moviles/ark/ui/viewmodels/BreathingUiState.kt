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
    //la pista de la app va primero y es la elegida al abrir; las de jamendo llegan despues
    val ambientTracks: List<AmbientTrackModel> = emptyList(),
    //id de la pista de la app, para mostrarla aparte de las de jamendo
    val defaultTrackId: String? = null,
    val selectedTrack: AmbientTrackModel? = null,
    val isMusicPlaying: Boolean = false,
    //true desde que se pide una pista hasta que de verdad suena (streaming cargando)
    val isTrackBuffering: Boolean = false,
    //true mientras se piden las pistas a jamendo
    val isLoadingTracks: Boolean = false,
    //true si jamendo no respondio (sin internet): solo se ofrecen las pistas que suenan offline
    val tracksLoadFailed: Boolean = false,
    //pista que no se pudo reproducir, para avisarlo en su fila
    val unplayableTrackId: String? = null,
    val showTrackSelector: Boolean = false
) {
    //la musica esta sonando o a punto de sonar: el boton muestra pausa
    val isMusicActive: Boolean
        get() = isMusicPlaying || isTrackBuffering

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
