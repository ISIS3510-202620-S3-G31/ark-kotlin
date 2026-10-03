package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.composite.Emotion

data class FeedbackUiState(
    val toolId: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val selectedEmotions: Set<Emotion> = emptySet(),
    val intensity: Float = 3f,
    val activeEmotion: Emotion? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)
