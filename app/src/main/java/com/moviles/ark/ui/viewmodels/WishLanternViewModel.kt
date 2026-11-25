package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.models.ToolLatencyTracker
import com.moviles.ark.domain.models.WishModel
import com.moviles.ark.domain.repositories.ToolInteractionRepository
import com.moviles.ark.domain.repositories.WishRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//viewmodel del farol de deseos; uno solo para las tres vistas (cielo, editor y lista)
//asi el deseo recien guardado en el editor llega directo al cielo
class WishLanternViewModel(
    private val wishRepository: WishRepository,
    //guarda que el usuario uso la herramienta, para las estadisticas
    private val toolInteractionRepository: ToolInteractionRepository? = null,
    //cierra la medicion de latencia que empezo con el toque en el home o el catalogo
    private val toolLatencyTracker: ToolLatencyTracker? = null,
    //true cuando hay internet
    private val connectivity: Flow<Boolean> = flowOf(true),
    //hilo para trabajo pesado de cpu, como calcular donde va cada farol
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(WishLanternUiState())
    val uiState: StateFlow<WishLanternUiState> = _uiState.asStateFlow()

    //cuando se abrio la herramienta, para saber cuanto tiempo la uso
    private val openedAtMillis = System.currentTimeMillis()

    //trabajo que escucha la lista completa; null mientras el usuario no haya abierto la lista
    private var fullListJob: Job? = null

    init {
        warmUpLanternCache()
        listenToSkyLanterns()
        listenToLitLanterns()
        listenToConnectivity()
    }

    //al abrir la herramienta se encienden los faroles de los 8 deseos mas recientes (se llena la cache)
    private fun warmUpLanternCache() {
        viewModelScope.launch {
            wishRepository.warmUpCache()
        }
    }

    //cada vez que cambian los deseos (guardar, editar o borrar) llegan los ids nuevos
    //y los faroles del cielo se vuelven a armar
    private fun listenToSkyLanterns() {
        viewModelScope.launch {
            wishRepository.getWishIds().collect { wishIds ->
                //calcular la posicion de muchos faroles es trabajo de cpu: se hace en Dispatchers.Default
                //asi el hilo principal (Main) queda libre para las animaciones
                val lanterns = withContext(defaultDispatcher) {
                    createSkyLanterns(wishIds)
                }
                _uiState.update { it.copy(skyLanterns = lanterns, isSkyLoading = false) }
            }
        }
    }

    //lista completa con el texto de cada deseo; room la lee en IO y el repositorio la ordena en Default
    //se escucha una sola vez: si ya se estaba escuchando no se vuelve a empezar
    private fun listenToFullList() {
        if (fullListJob != null) {
            return
        }
        fullListJob = viewModelScope.launch {
            wishRepository.getWishes().collect { wishes ->
                _uiState.update { it.copy(wishes = wishes, isListLoading = false) }
            }
        }
    }

    //arma un farol por cada deseo con su lugar en el cielo
    //corre en Dispatchers.Default
    private fun createSkyLanterns(wishIds: List<String>): List<SkyLantern> {
        val lanterns = mutableListOf<SkyLantern>()
        for (wishId in wishIds) {
            lanterns.add(createSkyLantern(wishId))
        }
        return lanterns
    }

    //el lugar sale del id del deseo, asi cada farol queda siempre en el mismo lugar
    private fun createSkyLantern(wishId: String): SkyLantern {
        //numero entre 0 y 9999 que siempre es el mismo para el mismo id
        val number = Math.floorMod(wishId.hashCode(), 10000)
        val x = (number % 100) / 100f
        val y = (number / 100) / 100f
        //entre 2 y 3.5 segundos para subir y bajar
        val floatDuration = 2000 + Math.floorMod(wishId.hashCode(), 1500)

        return SkyLantern(
            wishId = wishId,
            x = x,
            y = y,
            floatDurationMillis = floatDuration
        )
    }

    //cada vez que se enciende o se apaga un farol llegan los ids encendidos
    private fun listenToLitLanterns() {
        viewModelScope.launch {
            wishRepository.getLitWishIds().collect { litIds ->
                _uiState.update { it.copy(litWishIds = litIds) }
            }
        }
    }

    //cada vez que se pierde o vuelve el internet
    private fun listenToConnectivity() {
        viewModelScope.launch {
            connectivity.collect { isOnline ->
                _uiState.update { it.copy(isOnline = isOnline) }
            }
        }
    }

    //la pantalla termino su primer dibujo: se cierra la medicion de latencia
    fun onScreenRendered() {
        toolLatencyTracker?.onToolRendered(WishModel.TOOL_ID)
    }

    // ---------- cielo ----------

    //el usuario toco un farol del fondo: se abre su deseo y el farol se enciende
    fun onLanternTapped(wishId: String) {
        viewModelScope.launch {
            val wish = wishRepository.openWish(wishId)
            _uiState.update { it.copy(openedWish = wish) }
        }
    }

    //se cerro el deseo que estaba abierto
    fun closeOpenedWish() {
        _uiState.update { it.copy(openedWish = null) }
    }

    //el farol grande termino de volar: ya hace parte del cielo
    //no se guarda nada aqui porque el deseo ya quedo guardado al presionar guardar
    fun onLanternReleased() {
        _uiState.update { it.copy(wishToRelease = null) }
    }

    // ---------- navegacion entre vistas ----------

    //abre el editor vacio para escribir un deseo nuevo
    fun openEditorForNewWish() {
        _uiState.update {
            it.copy(
                currentView = WishLanternView.EDITOR,
                editorText = "",
                editingWishId = null,
                errorMessage = null
            )
        }
    }

    //abre el editor con el texto de un deseo para cambiarlo
    fun openEditorForWish(wish: WishModel) {
        _uiState.update {
            it.copy(
                currentView = WishLanternView.EDITOR,
                editorText = wish.text,
                editingWishId = wish.id,
                errorMessage = null
            )
        }
    }

    //abre la lista con todos los deseos
    fun openList() {
        _uiState.update { it.copy(currentView = WishLanternView.LIST, errorMessage = null) }
        //la lista completa se pide solo cuando el usuario la abre
        listenToFullList()
    }

    //boton atras dentro de la herramienta
    //devuelve true si ya estaba en el cielo, o sea que hay que salir de la herramienta
    fun goBack(): Boolean {
        val state = _uiState.value
        if (state.currentView == WishLanternView.SKY) {
            return true
        }

        if (state.currentView == WishLanternView.EDITOR && state.isEditing()) {
            //si estaba editando, vuelve a la lista de donde vino
            _uiState.update { it.copy(currentView = WishLanternView.LIST, errorMessage = null) }
        } else {
            //desde el editor de un deseo nuevo o desde la lista, vuelve al cielo
            _uiState.update { it.copy(currentView = WishLanternView.SKY, errorMessage = null) }
        }
        return false
    }

    // ---------- editor ----------

    //el usuario escribio algo; no deja pasar del maximo de letras
    fun onEditorTextChange(newText: String) {
        if (newText.length <= WishModel.MAX_TEXT_LENGTH) {
            _uiState.update { it.copy(editorText = newText) }
        }
    }

    //boton guardar del editor: crea un deseo nuevo o cambia el que se esta editando
    fun saveWish() {
        val state = _uiState.value
        if (!state.canSave()) {
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            if (state.isEditing()) {
                saveEditedWish(state.editingWishId!!, state.editorText)
            } else {
                saveNewWish(state.editorText)
            }
        }
    }

    //deseo nuevo: se guarda, se vuelve al cielo y el farol grande aparece encendido listo para volar
    private suspend fun saveNewWish(text: String) {
        val result = wishRepository.saveWish(text)
        if (result.isSuccess) {
            val savedWish = result.getOrNull()
            _uiState.update {
                it.copy(
                    isSaving = false,
                    currentView = WishLanternView.SKY,
                    wishToRelease = savedWish,
                    editorText = ""
                )
            }
        } else {
            _uiState.update {
                it.copy(isSaving = false, errorMessage = "We couldn't save your wish. Please try again.")
            }
        }
    }

    //deseo editado: se guarda el texto nuevo y se vuelve a la lista
    private suspend fun saveEditedWish(wishId: String, text: String) {
        val result = wishRepository.updateWish(wishId, text)
        if (result.isSuccess) {
            _uiState.update {
                it.copy(
                    isSaving = false,
                    currentView = WishLanternView.LIST,
                    editingWishId = null,
                    editorText = ""
                )
            }
        } else {
            _uiState.update {
                it.copy(isSaving = false, errorMessage = "We couldn't update your wish. Please try again.")
            }
        }
    }

    // ---------- lista ----------

    //el usuario toco borrar: primero se le pregunta si esta seguro
    fun askToDeleteWish(wish: WishModel) {
        _uiState.update { it.copy(wishToDelete = wish) }
    }

    //dijo que no quiere borrarlo
    fun cancelDeleteWish() {
        _uiState.update { it.copy(wishToDelete = null) }
    }

    //dijo que si: se borra el deseo
    fun confirmDeleteWish() {
        val wish = _uiState.value.wishToDelete ?: return
        _uiState.update { it.copy(wishToDelete = null) }

        viewModelScope.launch {
            val result = wishRepository.deleteWish(wish.id)
            if (result.isFailure) {
                _uiState.update { it.copy(errorMessage = "We couldn't delete your wish. Please try again.") }
            }

            //si el deseo borrado estaba esperando a volar, el farol grande vuelve a quedar apagado
            if (_uiState.value.wishToRelease?.id == wish.id) {
                _uiState.update { it.copy(wishToRelease = null) }
            }
        }
    }

    // ---------- terminar ----------

    //boton del chulito: el usuario termino de usar la herramienta
    //guarda la interaccion y devuelve cuantos segundos uso la herramienta, para el feedback
    fun finishTool(): Int {
        val durationSeconds = ((System.currentTimeMillis() - openedAtMillis) / 1000).toInt()
        val repository = toolInteractionRepository ?: return durationSeconds

        viewModelScope.launch {
            //NonCancellable: la pantalla se cierra enseguida y eso no debe cortar el guardado
            withContext(NonCancellable) {
                repository.recordCompletion(
                    toolId = WishModel.TOOL_ID,
                    durationSeconds = durationSeconds,
                    interactionType = WishModel.INTERACTION_TYPE,
                    timestamp = System.currentTimeMillis()
                )
            }
        }
        return durationSeconds
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                WishLanternViewModel(
                    wishRepository = app.container.wishRepository,
                    toolInteractionRepository = app.container.toolInteractionRepository,
                    toolLatencyTracker = app.container.toolLatencyTracker,
                    connectivity = app.container.networkConnectivityObserver.observe()
                )
            }
        }
    }
}
