package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.data.repositories.FakeToolRepository
import com.moviles.ark.domain.models.RecommendationContextResolver
import com.moviles.ark.domain.models.RecommendationDecision
import com.moviles.ark.domain.models.Tool
import com.moviles.ark.domain.models.ToolCategory
import com.moviles.ark.domain.models.ToolLatencyTracker
import com.moviles.ark.domain.repositories.MoodRepository
import com.moviles.ark.domain.repositories.ToolInteractionRepository
import com.moviles.ark.domain.repositories.ToolRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class HomeViewModel(
    private val toolRepository: ToolRepository,
    private val moodRepository: MoodRepository? = null,
    //elige la estrategia de recomendacion segun el contexto del usuario (#23)
    private val contextResolver: RecommendationContextResolver = RecommendationContextResolver(),
    //cronometro de la pregunta de negocio #1: arranca cuando el usuario toca una herramienta (#90)
    private val toolLatencyTracker: ToolLatencyTracker? = null,
    //herramientas que el usuario ya termino (room), para recomendar por frecuencia (#96)
    private val toolInteractionRepository: ToolInteractionRepository? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
    initialTools: List<Tool> = FakeToolRepository.sampleTools
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            isLoading = false,
            tools = initialTools,
            filteredTools = initialTools
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    //carga activa del catalogo; getTools() nunca termina, asi que antes de volver a cargar se cancela la anterior (#96)
    private var loadToolsJob: Job? = null

    //true mientras el usuario esta en una herramienta; al volver se recalculan las recomendaciones (#96)
    private var isAwayInTool = false

    init {
        checkTodayStatus()
        loadTools()
    }

    fun checkTodayStatus() {
        if (moodRepository != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val hasCheckedIn = moodRepository.hasCheckedInToday().getOrDefault(false)
                _uiState.value = _uiState.value.copy(isCheckInCompleted = hasCheckedIn)
            }
        }
    }

    fun loadTools() {
        loadToolsJob?.cancel()
        loadToolsJob = viewModelScope.launch(Dispatchers.IO) {
            toolRepository.getTools()
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Could not load tools"
                    )
                }
                .collect { rawTools ->
                    val decision = resolveDecision()
                    val recommendedTools = decision.strategy.recommend(rawTools, decision.context)
                    val filtered = applyCategoryFilter(recommendedTools, _uiState.value.selectedCategory)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        tools = recommendedTools,
                        filteredTools = filtered
                    )
                }
        }
    }

    fun onCategorySelect(category: ToolCategory?) {
        val currentTools = _uiState.value.tools
        val filtered = applyCategoryFilter(currentTools, category)
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            filteredTools = filtered
        )
    }

    fun onSurpriseMe() {
        val available = if (_uiState.value.filteredTools.isNotEmpty()) {
            _uiState.value.filteredTools
        } else {
            _uiState.value.tools
        }

        if (available.isNotEmpty()) {
            val randomTool = available.random()
            _uiState.value = _uiState.value.copy(surpriseTool = randomTool)
        }
    }

    //las tres entradas del home (catalogo, recomendacion y surprise me) llegan aqui
    fun onToolClick(toolId: String) {
        //el toque es el inicio de la medicion; la pantalla de la herramienta la cierra al dibujarse (#90)
        toolLatencyTracker?.start(toolId)
        _uiState.value = _uiState.value.copy(navigateToToolId = toolId)
    }

    fun onNavigatedToTool() {
        isAwayInTool = true
        _uiState.value = _uiState.value.copy(navigateToToolId = null)
    }

    //el home se vuelve a mostrar: si venia de una herramienta, puede haber una interaccion nueva en room (#96)
    fun onHomeShown() {
        if (!isAwayInTool) return
        isAwayInTool = false
        loadTools()
    }

    fun onCheckInSaved() {
        _uiState.value = _uiState.value.copy(isCheckInCompleted = true)
        loadTools()
    }

    //decide la estrategia con el ultimo check-in del usuario (#23) y las herramientas que termino (#96)
    private suspend fun resolveDecision(): RecommendationDecision {
        val latestCheckIn = moodRepository?.getLatestCheckIn()?.getOrNull()
        val from = now() - RecommendationContextResolver.USAGE_WINDOW_DAYS * DAY_MILLIS
        val interactions = toolInteractionRepository?.getInteractionsSince(from)?.getOrNull().orEmpty()
        return contextResolver.resolve(listOfNotNull(latestCheckIn), interactions)
    }

    private fun applyCategoryFilter(tools: List<Tool>, category: ToolCategory?): List<Tool> {
        return if (category == null) {
            tools
        } else {
            tools.filter { it.category == category }
        }
    }

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                HomeViewModel(
                    toolRepository = app.container.toolRepository,
                    moodRepository = app.container.moodRepository,
                    toolLatencyTracker = app.container.toolLatencyTracker,
                    toolInteractionRepository = app.container.toolInteractionRepository
                )
            }
        }
    }
}
