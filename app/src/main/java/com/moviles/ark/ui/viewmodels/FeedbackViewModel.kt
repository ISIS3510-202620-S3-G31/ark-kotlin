package com.moviles.ark.ui.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.entities.FeedbackEntity
import com.moviles.ark.domain.composite.CompoundMood
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.MoodComponent
import com.moviles.ark.domain.composite.SingleEmotion
import com.moviles.ark.domain.models.CheckInModel
import com.moviles.ark.domain.repositories.AnalyticsRepository
import com.moviles.ark.domain.repositories.MoodRepository
import com.moviles.ark.domain.repositories.ToolRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedbackViewModel(
    private val feedbackDao: FeedbackDao? = null,
    private val moodRepository: MoodRepository? = null,
    private val analyticsRepository: AnalyticsRepository? = null,
    private val toolRepository: ToolRepository? = null,
    savedStateHandle: SavedStateHandle? = null,
    initialToolId: String = ""
) : ViewModel() {

    private val privateUiState = MutableStateFlow(
        FeedbackUiState(
            toolId = savedStateHandle?.get<String>("toolId") ?: initialToolId
        )
    )
    val uiState: StateFlow<FeedbackUiState> = privateUiState.asStateFlow()

    fun setToolId(toolId: String) {
        privateUiState.value = privateUiState.value.copy(toolId = toolId)
    }

    fun onRatingChange(rating: Int) {
        privateUiState.value = privateUiState.value.copy(
            rating = rating,
            errorMessage = null
        )
    }

    fun onCommentChange(comment: String) {
        privateUiState.value = privateUiState.value.copy(
            comment = comment,
            errorMessage = null
        )
    }

    fun onEmotionToggle(emotion: Emotion) {
        val currentEmotions = privateUiState.value.selectedEmotions
        val isAdding = !currentEmotions.contains(emotion)
        val newEmotions = if (isAdding) {
            currentEmotions + emotion
        } else {
            currentEmotions - emotion
        }
        val newActive = if (newEmotions.contains(emotion)) {
            emotion
        } else {
            newEmotions.lastOrNull()
        }
        privateUiState.value = privateUiState.value.copy(
            selectedEmotions = newEmotions,
            activeEmotion = newActive,
            intensity = if (isAdding) 3f else privateUiState.value.intensity,
            errorMessage = null
        )
    }

    fun onIntensityChange(newIntensity: Float) {
        privateUiState.value = privateUiState.value.copy(
            intensity = newIntensity,
            errorMessage = null
        )
    }

    fun saveFeedback(onSavedCallback: () -> Unit = {}) {
        val state = privateUiState.value

        if (state.rating < 1 || state.rating > 5) {
            privateUiState.value = state.copy(errorMessage = "Please select a rating from 1 to 5 stars")
            return
        }

        privateUiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                // Save feedback to Room local database
                feedbackDao?.insertFeedback(
                    FeedbackEntity(
                        toolId = state.toolId,
                        rating = state.rating,
                        comment = state.comment,
                        createdAt = System.currentTimeMillis()
                    )
                )

                // Save post-tool emotion check-in if emotions were selected
                if (state.selectedEmotions.isNotEmpty() && moodRepository != null) {
                    val moodResult: MoodComponent = if (state.selectedEmotions.size == 1) {
                        SingleEmotion(state.selectedEmotions.first(), state.intensity.toInt())
                    } else {
                        val compound = CompoundMood()
                        for (emotion in state.selectedEmotions) {
                            compound.add(SingleEmotion(emotion, state.intensity.toInt()))
                        }
                        compound
                    }
                    val checkIn = CheckInModel(
                        mood = moodResult,
                        note = if (state.comment.isNotBlank()) "Post-tool feedback: ${state.comment}" else "Post-tool feedback"
                    )
                    moodRepository.saveCheckIn(checkIn)
                }

                // Log tool completion event to Firebase Analytics (#34)
                val format = toolRepository?.getToolById(state.toolId)?.format ?: "touch"
                analyticsRepository?.logToolEntryCompleted(
                    toolId = state.toolId.ifBlank { "custom_breathing" },
                    toolFormat = format
                )

                privateUiState.value = privateUiState.value.copy(
                    isLoading = false,
                    isSaved = true,
                    errorMessage = null
                )
                onSavedCallback()
            } catch (e: Exception) {
                Log.e("FeedbackViewModel", "Error saving feedback", e)
                privateUiState.value = privateUiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to save feedback"
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                FeedbackViewModel(
                    feedbackDao = app.container.feedbackDao,
                    moodRepository = app.container.moodRepository,
                    analyticsRepository = app.container.analyticsRepository,
                    toolRepository = app.container.toolRepository
                )
            }
        }
    }
}
