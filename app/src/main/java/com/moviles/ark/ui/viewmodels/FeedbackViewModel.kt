package com.moviles.ark.ui.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.moviles.ark.ArkApplication
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.ToolInteractionEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val backgroundScope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())
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

    // Modelo optimista (#166): valida con el modelo de dominio, avanza de inmediato la UI
    // y despacha copias de las entidades al hilo IO en segundo plano para su persistencia y manejo de errores
    fun saveFeedback(onSavedCallback: () -> Unit = {}) {
        val state = privateUiState.value

        // Regla de negocio delegada al modelo de dominio: validar el rango de calificación
        if (!FeedbackModel.isValidRating(state.rating)) {
            privateUiState.value = state.copy(
                errorMessage = "Please select a rating from ${FeedbackModel.MIN_RATING} to ${FeedbackModel.MAX_RATING} stars"
            )
            return
        }

        //  Modelo optimista: avanzar de inmediato en la UI sin bloquear al usuario ya que no es tan importnate y masn bien le mandamos una copia de los cosos que vamos a guardar y asis no se pierden
        privateUiState.value = state.copy(
            isSaved = true,
            isLoading = false,
            errorMessage = null
        )
        onSavedCallback()

        // 3. Crear copias inmutables de los datos actuales en el hilo principal antes de despachar al hilo IO
        val snapshotToolId = state.toolId
        val snapshotRating = state.rating
        val snapshotComment = state.comment
        val snapshotDuration = if (state.duration > 0) state.duration else 60
        val snapshotEmotions = state.selectedEmotions.toSet()
        val snapshotIntensity = state.intensity
        val nowMillis = System.currentTimeMillis()
        val userId = auth?.currentUser?.uid ?: "anonymous"

        // 4. Despachar a segundo plano en el hilo IO usando backgroundScope para que no se cancele al salir de la pantalla
        backgroundScope.launch(ioDispatcher) {
            try {
                val format = toolRepository?.getToolById(snapshotToolId)?.format ?: "touch"

                // A. Guardar copia de la interaccion en Room SQLite para obtener el interactionId generado (#154)
                val interaction = ToolInteractionEntity(
                    userId = userId,
                    toolId = snapshotToolId,
                    duration = snapshotDuration,
                    interactionType = format,
                    complete = true,
                    timestamp = Date(nowMillis),
                    isSynced = false
                )
                val interactionId = toolRecordDao?.insertInteraction(interaction)

                // B. Construir el modelo de dominio aplicando sus reglas de negocio y persistirlo
                val feedbackResult = FeedbackModel.create(
                    toolId = snapshotToolId,
                    rating = snapshotRating,
                    comment = snapshotComment,
                    interactionId = interactionId,
                    createdAt = nowMillis
                )

                feedbackResult.onSuccess { model ->
                    feedbackRepository?.saveFeedback(model)
                }.onFailure { error ->
                    Log.e("FeedbackViewModel", "Domain validation failure in FeedbackModel", error)
                }

                // C. Guardar copia del check-in emocional en segundo plano si se eligieron emociones
                if (snapshotEmotions.isNotEmpty() && moodRepository != null) {
                    val moodResult: MoodComponent = if (snapshotEmotions.size == 1) {
                        SingleEmotion(snapshotEmotions.first(), snapshotIntensity.toInt())
                    } else {
                        val compound = CompoundMood()
                        for (emotion in snapshotEmotions) {
                            compound.add(SingleEmotion(emotion, snapshotIntensity.toInt()))
                        }
                        compound
                    }
                    val checkIn = CheckInModel(
                        mood = moodResult,
                        note = if (snapshotComment.isNotBlank()) "Post-tool feedback: ${snapshotComment.trim()}" else "Post-tool feedback"
                    )
                    moodRepository.saveCheckIn(checkIn)
                }

                //  Registrar evento de finalizacion de herramienta en Firebase Analytics (#34)
                analyticsRepository?.logToolEntryCompleted(
                    toolId = snapshotToolId.ifBlank { "custom_breathing" },
                    toolFormat = format
                )

                Log.d("FeedbackViewModel", "Optimistic feedback persistence finished successfully on IO thread")
            } catch (e: Exception) {
                // Manejo de errores en el hilo IO sin romper ni bloquear la experiencia del usuario porqsi algo sale mal con el guardado
                Log.e("FeedbackViewModel", "Error persisting feedback in background on IO thread", e)
                CrashlyticsHelper.logNonFatal(
                    componentName = "FeedbackViewModel",
                    action = "saveFeedbackOptimistic",
                    throwable = e,
                    extraKeys = mapOf(
                        "toolId" to snapshotToolId,
                        "rating" to snapshotRating
                    )
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
                    auth = app.container.auth,
                    backgroundScope = app.container.applicationScope
                )
            }
        }
    }
}
