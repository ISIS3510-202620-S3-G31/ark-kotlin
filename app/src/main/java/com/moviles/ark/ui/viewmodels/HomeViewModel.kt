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
import com.moviles.ark.domain.repositories.MoodRepository
import com.moviles.ark.domain.repositories.ToolRepository
import kotlinx.coroutines.Dispatchers
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
        viewModelScope.launch(Dispatchers.IO) {
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

    fun onToolClick(toolId: String) {
        _uiState.value = _uiState.value.copy(navigateToToolId = toolId)
    }

    fun onNavigatedToTool() {
        _uiState.value = _uiState.value.copy(navigateToToolId = null)
    }

    fun onCheckInSaved() {
        _uiState.value = _uiState.value.copy(isCheckInCompleted = true)
        loadTools()
    }

    //decide la estrategia con el ultimo check-in del usuario (#23)
    //todavia no hay repositorio de interacciones con herramientas, por eso la lista va vacia
    private suspend fun resolveDecision(): RecommendationDecision {
        val latestCheckIn = moodRepository?.getLatestCheckIn()?.getOrNull()
        return contextResolver.resolve(listOfNotNull(latestCheckIn), emptyList())
    }

    private fun applyCategoryFilter(tools: List<Tool>, category: ToolCategory?): List<Tool> {
        return if (category == null) {
            tools
        } else {
            tools.filter { it.category == category }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                HomeViewModel(
                    toolRepository = app.container.toolRepository,
                    moodRepository = app.container.moodRepository
                )
            }
        }
    }
}
