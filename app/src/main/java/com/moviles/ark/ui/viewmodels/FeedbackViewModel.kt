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
import com.moviles.ark.domain.models.FeedbackModel
import com.moviles.ark.domain.repositories.FeedbackRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class FeedbackViewModel(
    private val feedbackRepository: FeedbackRepository? = null,
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
        if (comment.length <= FeedbackModel.MAX_COMMENT_LENGTH) {
            privateUiState.value = privateUiState.value.copy(
                comment = comment,
                errorMessage = null
            )
        }
    }

    //la regla de que toque elige, activa o quita una emocion vive en EmotionSelection
    fun onEmotionToggle(emotion: Emotion) {
        privateUiState.value = privateUiState.value.copy(
            selection = privateUiState.value.selection.toggle(emotion),
            errorMessage = null
        )
    }

    //el slider solo cambia la intensidad de la emocion activa
    fun onIntensityChange(newIntensity: Float) {
        privateUiState.value = privateUiState.value.copy(
            selection = privateUiState.value.selection.withActiveIntensity(newIntensity.roundToInt()),
            errorMessage = null
        )
    }

    // Persiste en Room SQLite; el SyncManager lo sube a Firestore cuando hay internet
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

                //la interaccion ya la guardo la herramienta al terminar (o al abandonarla); aqui va la calificacion
                //las emociones se guardan con el feedback: el check-in del dia solo lo hace el usuario y no cambia
                feedbackRepository?.saveFeedback(
                    FeedbackModel(
                        toolId = state.toolId,
                        rating = state.rating,
                        comment = state.comment.trim(),
                        emotions = state.selection.toParts(),
                        createdAt = nowMillis
                    )
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
                    feedbackRepository = app.container.feedbackRepository
                )
            }
        }
    }
}
