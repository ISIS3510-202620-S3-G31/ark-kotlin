package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.models.EmotionSelection

data class FeedbackUiState(
    val toolId: String = "",
    val duration: Int = 0,
    val rating: Int = 0,
    val comment: String = "",
    //emociones elegidas, cada una con su intensidad, y la que ajusta el slider
    val selection: EmotionSelection = EmotionSelection(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
) {
    val selectedEmotions: Set<Emotion> get() = selection.emotions
    val activeEmotion: Emotion? get() = selection.active
    val intensity: Float get() = selection.activeIntensity.toFloat()
}
