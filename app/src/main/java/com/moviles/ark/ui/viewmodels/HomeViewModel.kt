package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.data.remote.CrashlyticsHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import com.moviles.ark.data.local.AvatarPreferences
import com.moviles.ark.data.repositories.ToolRepositoryImpl
import com.moviles.ark.domain.adapters.WeatherServiceAdapter
import com.moviles.ark.domain.models.CachedRecommendation
import com.moviles.ark.domain.models.CheckInModel
import com.moviles.ark.domain.models.RecommendationContextResolver
import com.moviles.ark.domain.models.RecommendationDecision
import com.moviles.ark.domain.models.Tool
import com.moviles.ark.domain.models.ToolCategory
import com.moviles.ark.domain.models.ToolLatencyTracker
import com.moviles.ark.domain.models.WeatherSuggestionEngine
import com.moviles.ark.domain.repositories.LocationRepository
import com.moviles.ark.domain.repositories.MoodRepository
import com.moviles.ark.domain.repositories.RecommendationRepository
import com.moviles.ark.domain.repositories.ToolInteractionRepository
import com.moviles.ark.domain.strategies.DefaultRecommendationStrategy
import com.moviles.ark.domain.strategies.RecommendationStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class HomeViewModel(
    private val toolRepository: com.moviles.ark.domain.repositories.ToolRepository,
    private val moodRepository: MoodRepository? = null,
    //elige la estrategia de recomendacion segun el contexto del usuario
    private val contextResolver: RecommendationContextResolver = RecommendationContextResolver(),
    //cronometro de la pregunta de negocio #1: arranca cuando el usuario toca una herramienta
    private val toolLatencyTracker: ToolLatencyTracker? = null,
    //herramientas que el usuario ya termino para tener la info parecomendar por frecuencia
    private val toolInteractionRepository: ToolInteractionRepository? = null,
    //ultima recomendacion guardada en disco para cuando no hay internet
    private val recommendationRepository: RecommendationRepository? = null,
    //true si hay internet en este momento
    private val isOnline: () -> Boolean = { true },
    //clima de afuera para la tarjeta de clima; necesita la ubicacion del telefono
    private val weatherServiceAdapter: WeatherServiceAdapter? = null,
    private val locationRepository: LocationRepository? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
    initialTools: List<Tool> = ToolRepositoryImpl.sampleTools,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            isLoading = false,
            tools = initialTools.filter { it.isImplemented },
            filteredTools = initialTools.filter { it.isImplemented }
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    //carga activa del catalogo getToolsnunca termina, asi que antes de volver a cargar se cancela la anterior
    private var loadToolsJob: Job? = null

    //true mientras el usuario esta en una herramienta; al volver se recalculan las recomendaciones
    private var isAwayInTool = false

    //unica estrategia de recomendacion del home; el resolver crea solo la que pide el contexto y se guarda aqui
    //arranca con el orden por defecto mientras llegan el check-in y el historial
    private var activeStrategy: RecommendationStrategy = DefaultRecommendationStrategy()

    init {
        checkTodayStatus()
        loadTools()
        loadWeatherSuggestion()
        observeAvatar()
    }

    //el clima va aparte del resolver de estrategias: no cambia el orden del catalogo, solo llena su propia tarjeta
    //se valida la cache en disco (TTL de 1 hora) para tener clima sin red; si no hay clima o no tiene sugerencia la tarjeta no sale
    fun loadWeatherSuggestion() {
        val adapter = weatherServiceAdapter ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val location = locationRepository?.getCurrentLocation()
            val latitude = location?.latitude
            val longitude = location?.longitude

            val weather = if (latitude != null && longitude != null) {
                adapter.getCurrentWeather(latitude, longitude).getOrNull()
            } else {
                (adapter as? com.moviles.ark.data.remote.apis.OpenMeteoAdapterImpl)?.getCachedWeatherIfValid()
            }

            val currentTools = _uiState.value.tools.ifEmpty { ToolRepositoryImpl.sampleTools }
            val suggestion = weather?.let { WeatherSuggestionEngine(it).suggest(currentTools) }

            _uiState.update { it.copy(weatherSuggestion = suggestion) }
        }
    }

    private fun observeAvatar() {
        viewModelScope.launch {
            AvatarPreferences.currentAvatarId.collect { id ->
                _uiState.value = _uiState.value.copy(avatarId = id)
            }
        }
    }

    fun checkTodayStatus() {
        if (moodRepository != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val hasCheckedIn = moodRepository.hasCheckedInToday().getOrDefault(false)
                val checkIn = if (hasCheckedIn) moodRepository.getLatestCheckIn().getOrNull() else null
                _uiState.value = _uiState.value.copy(
                    isCheckInCompleted = hasCheckedIn,
                    todayCheckIn = checkIn
                )
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
                .collect { catalogTools ->
                    //solo se recomiendan y se muestran las herramientas que ya tienen pantalla
                    val rawTools = catalogTools.filter { it.isImplemented }
                    val recommendedTools = recommend(rawTools)
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

    //elige una herramienta al azar del catalogo guardado en room, en Dispatchers.Default y sin red
    //solo entre las que ya estan hechas; si falla la lectura se abre la respiracion
    fun onSurpriseMe() {
        viewModelScope.launch(defaultDispatcher) {
            try {
                val localTools = toolRepository.getTools().first()
                val available = localTools.ifEmpty { _uiState.value.tools }.ifEmpty { ToolRepositoryImpl.sampleTools }
                    .filter { it.isImplemented }

                //abre la herramienta elegida de una vez, por el mismo camino que las demas entradas del home
                if (available.isNotEmpty()) {
                    onToolClick(available.random().id)
                } else {
                    launchBreathingFallback()
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                CrashlyticsHelper.logNonFatal(
                    componentName = "HomeViewModel",
                    action = "onSurpriseMe",
                    throwable = e
                )
                launchBreathingFallback()
            }
        }
    }

    //si no se pudo leer el catalogo se abre la respiracion, que siempre esta en el telefono
    private fun launchBreathingFallback() {
        onToolClick(WeatherSuggestionEngine.CALM_TOOL_ID)
    }

    //las tres entradas del home (catalogo, recomendacion y surprise me) llegan aqui
    fun onToolClick(toolId: String) {
        //el toque es el inicio de la medicion; la pantalla de la herramienta la cierra al dibujarse
        toolLatencyTracker?.start(toolId)
        _uiState.value = _uiState.value.copy(navigateToToolId = toolId)
    }

    fun onNavigatedToTool() {
        isAwayInTool = true
        _uiState.value = _uiState.value.copy(navigateToToolId = null)
    }

    //el home se vuelve a mostrar: valida el estado del check-in diario y si venia de una herramienta recalcula recomendaciones
    fun onHomeShown() {
        checkTodayStatus()
        if (!isAwayInTool) return
        isAwayInTool = false
        loadTools()
    }

    fun onCheckInSaved() {
        viewModelScope.launch(Dispatchers.IO) {
            val checkIn = moodRepository?.getLatestCheckIn()?.getOrNull()
            _uiState.value = _uiState.value.copy(
                isCheckInCompleted = true,
                todayCheckIn = checkIn
            )
        }
        loadTools()
    }

    //online-first: con internet se decide con los datos frescos y el resultado se guarda en disco;
    //sin internet se usa lo guardado hoy y, si no hay, se decide en el telefono con lo que haya guardado
    private suspend fun recommend(rawTools: List<Tool>): List<Tool> {
        val online = isOnline()
        if (!online) {
            val cached = recommendationRepository?.getLastRecommendation()
            val latestCheckInAt = moodRepository?.getLatestCheckIn()?.getOrNull()?.timestamp
            if (cached != null && cached.isFresh(now(), latestCheckInAt)) return cached.applyTo(rawTools)
        }

        val decision = resolveDecision()
        activeStrategy = decision.strategy
        val recommendedTools = activeStrategy.recommend(rawTools, decision.context)
        if (online) {
            recommendationRepository?.saveLastRecommendation(
                CachedRecommendation(decision.reason, recommendedTools.map { it.id }, now())
            )
        }
        return recommendedTools
    }

    //decide la estrategia con el ultimo check-in del usuario y las herramientas que termino
    //si algo falla al leer los datos se usa el orden por defecto
    private suspend fun resolveDecision(): RecommendationDecision = runCatching {
        val latestCheckIn = moodRepository?.getLatestCheckIn()?.getOrNull()
        val from = now() - RecommendationContextResolver.USAGE_WINDOW_DAYS * DAY_MILLIS
        val interactions = toolInteractionRepository?.getInteractionsSince(from)?.getOrNull().orEmpty()
        //decidir es solo calculo, por eso va en default y no en io
        withContext(Dispatchers.Default) { contextResolver.resolve(listOfNotNull(latestCheckIn), interactions) }
    }.getOrElse { error ->
        //si el home cancelo la carga no es un error de datos, se deja pasar la cancelacion
        if (error is CancellationException) throw error
        contextResolver.fallback()
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
                    toolInteractionRepository = app.container.toolInteractionRepository,
                    recommendationRepository = app.container.recommendationRepository,
                    isOnline = app.container.networkConnectivityObserver::isConnected,
                    weatherServiceAdapter = app.container.weatherServiceAdapter,
                    locationRepository = app.container.locationRepository
                )
            }
        }
    }
}
