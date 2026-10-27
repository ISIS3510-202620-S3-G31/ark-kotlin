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
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.ToolInteractionEntity
import com.moviles.ark.domain.composite.CompoundMood
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.MoodComponent
import com.moviles.ark.domain.composite.SingleEmotion
import com.moviles.ark.domain.models.CheckInModel
import com.moviles.ark.domain.models.FeedbackModel
import com.moviles.ark.domain.repositories.AnalyticsRepository
import com.moviles.ark.domain.repositories.FeedbackRepository
import com.moviles.ark.domain.repositories.MoodRepository
import com.moviles.ark.domain.repositories.ToolRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class FeedbackViewModel(
    private val feedbackRepository: FeedbackRepository? = null,
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

    // Persiste en Room SQLite y sincroniza a Firestore desacoplado via FeedbackRepository
    // Vincula la calificacion tanto a toolId como a interactionId
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

                // 1. Grabar la interaccion en Room SQLite para obtener el interactionId generado
                val interactionId = toolRecordDao?.insertInteraction(
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

                // 2. Persistir ratings (1 a 5 estrellas) y comentarios en Room vinculados a toolId e interactionId, sincronizando a Firestore
                val feedbackModel = FeedbackModel(
                    toolId = state.toolId,
                    rating = state.rating,
                    comment = state.comment.trim(),
                    createdAt = nowMillis,
                    interactionId = interactionId
                )
                feedbackRepository?.saveFeedback(feedbackModel)

                // 3. Guardar el post-tool emotion check-in si se seleccionaron emociones
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

                // 4. Registrar evento de finalizacion de herramienta en Firebase Analytics
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
                    feedbackRepository = app.container.feedbackRepository,
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
