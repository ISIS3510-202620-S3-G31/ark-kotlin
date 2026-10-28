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
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.SingleEmotion
import com.moviles.ark.domain.models.MoodCheckInModel
import com.moviles.ark.domain.models.FeedbackModel
import com.moviles.ark.domain.models.ToolInteractionModel
import com.moviles.ark.domain.repositories.FeedbackRepository
import com.moviles.ark.domain.repositories.MoodRepository
import com.moviles.ark.domain.repositories.ToolInteractionRepository
import com.moviles.ark.domain.repositories.ToolRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedbackViewModel(
    private val feedbackRepository: FeedbackRepository? = null,
    private val toolInteractionRepository: ToolInteractionRepository? = null,
    private val moodRepository: MoodRepository? = null,
    private val toolRepository: ToolRepository? = null,
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
            intensity = if (isAdding) SingleEmotion.DEFAULT_INTENSITY.toFloat() else privateUiState.value.intensity,
            errorMessage = null
        )
    }

    // Persiste en Room SQLite y sincroniza a Firestore desacoplado via FeedbackRepository
    // Vincula la calificacion tanto a toolId como a interactionId
    fun saveFeedback(onSavedCallback: () -> Unit = {}) {
        val state = privateUiState.value

        //la regla de la calificacion vive en el modelo
        val ratingError = FeedbackModel.ratingError(state.rating)
        if (ratingError != null) {
            privateUiState.value = state.copy(errorMessage = ratingError)
            return
        }

        privateUiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                val nowMillis = System.currentTimeMillis()
                val format = toolRepository?.getToolById(state.toolId)?.format ?: ToolInteractionModel.DEFAULT_INTERACTION_TYPE
                val durationSeconds = if (state.duration > 0) state.duration else ToolInteractionModel.DEFAULT_DURATION_SECONDS

                // 1. Grabar la interaccion para obtener el interactionId generado
                val interactionId = toolInteractionRepository
                    ?.recordCompletion(state.toolId, durationSeconds, format, nowMillis)
                    ?.getOrThrow()

                // 2. Persistir la calificacion y el comentario vinculados a toolId e interactionId
                feedbackRepository?.saveFeedback(
                    FeedbackModel(
                        toolId = state.toolId,
                        rating = state.rating,
                        comment = state.comment.trim(),
                        createdAt = nowMillis,
                        interactionId = interactionId
                    )
                )

                // 3. Guardar el check-in de emociones si se eligio alguna; el modelo arma el animo
                if (state.selectedEmotions.isNotEmpty() && moodRepository != null) {
                    moodRepository.saveCheckIn(MoodCheckInModel.afterTool(state.selectedEmotions, state.intensity.toInt(), state.comment))
                }
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
                    feedbackRepository = app.container.feedbackRepository,
                    toolInteractionRepository = app.container.toolInteractionRepository,
                    moodRepository = app.container.moodRepository,
                    toolRepository = app.container.toolRepository
                )
            }
        }
    }
}
