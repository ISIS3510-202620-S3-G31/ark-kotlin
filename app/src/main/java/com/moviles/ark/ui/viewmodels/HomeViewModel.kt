package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.repositories.UserRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import com.moviles.ark.domain.adapters.WeatherServiceAdapter
import com.moviles.ark.domain.models.Avatar
import com.moviles.ark.domain.models.CachedRecommendation
import com.moviles.ark.domain.models.MoodCheckInModel
import com.moviles.ark.domain.models.RecommendationContextResolver
import com.moviles.ark.domain.models.RecommendationReason
import com.moviles.ark.domain.models.RecommendationDecision
import com.moviles.ark.domain.models.ToolModel
import com.moviles.ark.domain.models.ToolCategory
import com.moviles.ark.domain.models.ToolEntryPoint
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
import kotlinx.coroutines.delay
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
    //nombre guardado en el telefono para el saludo y foto de perfil; null en previews
    private val userRepository: UserRepository? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
    initialTools: List<ToolModel> = ToolModel.DEFAULT_CATALOG,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ToolModel.implemented(initialTools).let { tools ->
            HomeUiState(
                isLoading = false,
                tools = tools,
                filteredTools = tools,
                availableCategories = ToolModel.availableCategories(tools, null),
                recommendedTool = tools.firstOrNull()
            )
        }
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    //carga activa del catalogo getToolsnunca termina, asi que antes de volver a cargar se cancela la anterior
    private var loadToolsJob: Job? = null

    //true mientras el usuario esta en una herramienta; al volver se recalculan las recomendaciones
    private var isAwayInTool = false

    //el check-in se ofrece solo una vez por apertura del home
    private var hasPromptedCheckIn = false

    //unica estrategia de recomendacion del home; el resolver crea solo la que pide el contexto y se guarda aqui
    //arranca con el orden por defecto mientras llegan el check-in y el historial
    private var activeStrategy: RecommendationStrategy = DefaultRecommendationStrategy()

    init {
        checkTodayStatus()
        loadTools()
        loadWeatherSuggestion()
        observeAvatar()
        observeUserName()
    }

    //el clima va aparte del resolver de estrategias: no cambia el orden del catalogo, solo llena su propia tarjeta
    //se valida la cache en disco (TTL de 1 hora) para tener clima sin red; si no hay clima la tarjeta queda neutral
    private var lastWeatherLoadAt = 0L

    fun loadWeatherSuggestion() {
        //la tarjeta sale de una vez con la sugerencia neutral y se cambia cuando llega el clima
        if (_uiState.value.weatherSuggestion == null) {
            val currentTools = _uiState.value.tools.ifEmpty { ToolModel.DEFAULT_CATALOG }
            _uiState.update { it.copy(weatherSuggestion = WeatherSuggestionEngine.loadingSuggestion(currentTools)) }
        }
        val adapter = weatherServiceAdapter ?: return
        lastWeatherLoadAt = now()
        viewModelScope.launch(Dispatchers.IO) {
            val location = locationRepository?.getCurrentLocation()
            val latitude = location?.latitude
            val longitude = location?.longitude

            //sin internet la consulta falla: se usa el ultimo clima guardado si aun vale
            val weather = if (latitude != null && longitude != null) {
                adapter.getCurrentWeather(latitude, longitude).getOrNull() ?: adapter.getCachedWeather()
            } else {
                adapter.getCachedWeather()
            }

            val currentTools = _uiState.value.tools.ifEmpty { ToolModel.DEFAULT_CATALOG }
            val suggestion = weather?.let { WeatherSuggestionEngine(it).suggest(currentTools) }
                ?: WeatherSuggestionEngine.defaultNeutralSuggestion(currentTools)

            _uiState.update { it.copy(weatherSuggestion = suggestion) }
        }
    }

    //nombre del perfil guardado en el telefono: el saludo sale al instante y sin red
    private fun observeUserName() {
        val repository = userRepository ?: return
        viewModelScope.launch {
            repository.observeCachedName().collect { name -> _uiState.update { it.copy(userName = name) } }
        }
        //recien iniciada la sesion el nombre aun no esta en el telefono (antes solo llegaba al abrir perfil):
        //se trae el perfil de firestore, o de room sin red, y el saludo cambia solo
        if (repository.cachedProfile()?.name.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) { repository.refreshProfile() }
        }
    }

    //avatar y foto viven en room por cuenta (y en firestore); el home muestra lo mismo que la pantalla de perfil
    private fun observeAvatar() {
        val repository = userRepository ?: return
        viewModelScope.launch {
            repository.observeProfile().collect { profile ->
                _uiState.update {
                    it.copy(
                        avatarId = profile?.avatarId ?: Avatar.DEFAULT_ID,
                        avatarPhotoPath = profile?.visiblePhotoPath()
                    )
                }
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
                //sin check-in hoy se ofrece una sola vez, con una pausa para que el home alcance a dibujarse
                if (!hasCheckedIn && !hasPromptedCheckIn) {
                    hasPromptedCheckIn = true
                    delay(CHECK_IN_PROMPT_DELAY_MILLIS)
                    _uiState.update { it.copy(showCheckInPrompt = true) }
                }
            }
        }
    }

    //la pantalla ya abrio el check-in que se ofrecio
    fun onCheckInPromptShown() {
        _uiState.update { it.copy(showCheckInPrompt = false) }
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
                    val rawTools = ToolModel.implemented(catalogTools)
                    val (recommendedTools, reason) = recommend(rawTools)
                    val selected = _uiState.value.selectedCategory
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        tools = recommendedTools,
                        filteredTools = ToolModel.inCategory(recommendedTools, selected),
                        availableCategories = ToolModel.availableCategories(recommendedTools, selected),
                        //la estrategia deja primero la herramienta que recomienda
                        recommendedTool = recommendedTools.firstOrNull(),
                        recommendationReason = reason
                    )
                }
        }
    }

    fun onCategorySelect(category: ToolCategory?) {
        val currentTools = _uiState.value.tools
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            filteredTools = ToolModel.inCategory(currentTools, category),
            availableCategories = ToolModel.availableCategories(currentTools, category)
        )
    }

    //elige una herramienta al azar del catalogo guardado en el telefono, en Dispatchers.Default y sin red
    //el modelo decide entre cuales; si no se pudo leer el catalogo se abre la de respaldo
    fun onSurpriseMe() {
        viewModelScope.launch(defaultDispatcher) {
            val localTools = toolRepository.getLocalTools().getOrNull()
            val pick = localTools?.let { tools -> ToolModel.surprisePick(tools.ifEmpty { _uiState.value.tools }.ifEmpty { ToolModel.DEFAULT_CATALOG }) }
            //abre la herramienta elegida de una vez, por el mismo camino que las demas entradas del home
            onToolClick(pick?.id ?: ToolModel.FALLBACK_TOOL_ID, ToolEntryPoint.SURPRISE_ME)
        }
    }

    //todas las entradas a una herramienta (catalogo, recomendacion, clima y surprise me) llegan aqui
    fun onToolClick(toolId: String, entryPoint: ToolEntryPoint = ToolEntryPoint.CATALOG) {
        //el toque es el inicio de la medicion; la pantalla de la herramienta la cierra al dibujarse
        toolLatencyTracker?.start(toolId, entryPoint)
        _uiState.value = _uiState.value.copy(navigateToToolId = toolId)
    }

    fun onNavigatedToTool() {
        isAwayInTool = true
        _uiState.value = _uiState.value.copy(navigateToToolId = null)
    }

    //el home se vuelve a mostrar: valida el estado del check-in diario y si venia de una herramienta recalcula recomendaciones
    fun onHomeShown() {
        checkTodayStatus()
        //el home vive toda la sesion: al volver se pide el clima otra vez si ya paso lo que dura su cache
        //(antes la tarjeta se quedaba con el clima de cuando se abrio la app, por ejemplo "Sunny" de noche)
        if (now() - lastWeatherLoadAt >= WEATHER_REFRESH_MILLIS) loadWeatherSuggestion()
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
    //devuelve el orden y por que se eligio, para que la tarjeta lo explique
    private suspend fun recommend(rawTools: List<ToolModel>): Pair<List<ToolModel>, RecommendationReason> {
        val online = isOnline()
        if (!online) {
            val cached = recommendationRepository?.getLastRecommendation()
            val latestCheckInAt = moodRepository?.getLatestCheckIn()?.getOrNull()?.timestamp
            if (cached != null && cached.isFresh(now(), latestCheckInAt)) return cached.applyTo(rawTools) to cached.reason
        }

        val decision = resolveDecision()
        activeStrategy = decision.strategy
        val recommendedTools = activeStrategy.recommend(rawTools, decision.context)
        if (online) {
            recommendationRepository?.saveLastRecommendation(
                CachedRecommendation(decision.reason, recommendedTools.map { it.id }, now())
            )
        }
        return recommendedTools to decision.reason
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

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val CHECK_IN_PROMPT_DELAY_MILLIS = 1500L
        //igual al tiempo que la cache del clima sirve para el mismo lugar (OpenMeteoAdapterImpl.FRESH_TTL_MILLIS)
        private const val WEATHER_REFRESH_MILLIS = 15 * 60 * 1000L

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
                    locationRepository = app.container.locationRepository,
                    userRepository = app.container.userRepository
                )
            }
        }
    }
}
