package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.composite.Emotion

//estado inmutable de la pantalla de check-in con soporte para coordenadas gps y nombre de la ciudad
data class MoodCheckInUiState(
    val selectedEmotions: Set<Emotion> = emptySet(),
    val activeEmotion: Emotion? = null,
    val intensity: Float = 3f,
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val cityName: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)