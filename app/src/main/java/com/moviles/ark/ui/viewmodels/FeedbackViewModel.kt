package com.moviles.ark.ui.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.moviles.ark.ArkApplication
import com.moviles.ark.data.local.daos.FeedbackDao
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.FeedbackEntity
import com.moviles.ark.data.local.entities.ToolInteractionEntity
import com.moviles.ark.domain.composite.CompoundMood
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.MoodComponent
import com.moviles.ark.domain.composite.SingleEmotion
import com.moviles.ark.domain.models.CheckInModel
import com.moviles.ark.domain.repositories.AnalyticsRepository
import com.moviles.ark.domain.repositories.MoodRepository
import com.moviles.ark.domain.repositories.ToolRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class FeedbackViewModel(
    private val feedbackDao: FeedbackDao? = null,
    private val toolRecordDao: ToolRecordDao? = null,
    private val moodRepository: MoodRepository? = null,
    private val analyticsRepository: AnalyticsRepository? = null,
    private val toolRepository: ToolRepository? = null,
    private val auth: FirebaseAuth? = null,
    savedStateHandle: SavedStateHandle? = null,
    initialToolId: String = "",
    initialDuration: Int = 0,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val privateUiState = MutableStateFlow(
        FeedbackUiState(
            toolId = savedStateHandle?.get<String>("toolId") ?: initialToolId,
            duration = savedStateHandle?.get<Int>("duration")
                ?: savedStateHandle?.get<String>("duration")?.toIntOrNull()
                ?: initialDuration
        )
    )
    val uiState: StateFlow<FeedbackUiState> = privateUiState.asStateFlow()

    fun setToolId(toolId: String) {
        privateUiState.value = privateUiState.value.copy(toolId = toolId)
    }

    fun setDuration(duration: Int) {
        privateUiState.value = privateUiState.value.copy(duration = duration)
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

    // Persiste en paralelo en Room SQLite usando Dispatchers.IO (#154)
    // Graba ToolInteractionEntity y FeedbackEntity concurrentemente, 100% offline
    fun saveFeedback(onSavedCallback: () -> Unit = {}) {
        val state = privateUiState.value

        if (state.rating < 1 || state.rating > 5) {
            privateUiState.value = state.copy(errorMessage = "Please select a rating from 1 to 5 stars")
            return
        }

        privateUiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                val nowMillis = System.currentTimeMillis()
                val userId = auth?.currentUser?.uid ?: "anonymous"
                val format = toolRepository?.getToolById(state.toolId)?.format ?: "touch"
                val durationSeconds = if (state.duration > 0) state.duration else 60

                // Grabar en paralelo en Room SQLite usando Dispatchers.IO (#154)
                coroutineScope {
                    launch {
                        toolRecordDao?.insertInteraction(
                            ToolInteractionEntity(
                                userId = userId,
                                toolId = state.toolId,
                                duration = durationSeconds,
                                interactionType = format,
                                complete = true,
                                timestamp = Date(nowMillis),
                                isSynced = false
                            )
                        )
                    }

                    launch {
                        feedbackDao?.insertFeedback(
                            FeedbackEntity(
                                toolId = state.toolId,
                                rating = state.rating,
                                comment = state.comment,
                                createdAt = nowMillis,
                                isSynced = false
                            )
                        )
                    }
                }

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
                analyticsRepository?.logToolEntryCompleted(
                    toolId = state.toolId.ifBlank { "custom_breathing" },
                    toolFormat = format
                )

                privateUiState.value = privateUiState.value.copy(
                    isLoading = false,
                    isSaved = true,
                    errorMessage = null
                )
                withContext(Dispatchers.Main) {
                    onSavedCallback()
                }
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
                    toolRecordDao = app.container.toolRecordDao,
                    moodRepository = app.container.moodRepository,
                    analyticsRepository = app.container.analyticsRepository,
                    toolRepository = app.container.toolRepository,
                    auth = app.container.auth
                )
            }
        }
    }
}
