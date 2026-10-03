package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.AmbientTrackModel

/**
 * Breathing exercise phases.
 */
enum class BreathingPhase(val label: String, val instruction: String) {
    READY("Ready", "Tap Start to begin your breathing exercise"),
    INHALE("Inhale", "Breathe in deeply through your nose"),
    HOLD("Hold", "Hold your breath calmly"),
    EXHALE("Exhale", "Release slowly through your mouth")
}

/**
 * UI State for Custom Breathing tool exercise with Jamendo ambient music integration (#7).
 */
data class BreathingUiState(
    val phase: BreathingPhase = BreathingPhase.READY,
    val isRunning: Boolean = false,
    val completedCycles: Int = 0,
    val secondsRemainingInPhase: Int = 4,
    val inhaleDuration: Int = 4,
    val holdDuration: Int = 4,
    val exhaleDuration: Int = 4,
    val phaseProgress: Float = 0f, // 0.0f to 1.0f progress within current phase
    // Estado de musica ambiental (#7)
    val ambientTracks: List<AmbientTrackModel> = emptyList(),
    val selectedTrack: AmbientTrackModel? = null,
    val isMusicPlaying: Boolean = false,
    val isLoadingMusic: Boolean = false,
    val showTrackSelector: Boolean = false
)
