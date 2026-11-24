package com.moviles.ark.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.domain.repositories.FortuneRepository
import com.moviles.ark.domain.repositories.ToolInteractionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//viewmodel para la galleta de la fortuna que gestiona estado y comunica con el repositorio
class FortuneCookieViewModel(
    private val fortuneRepository: FortuneRepository,
    private val toolInteractionRepository: ToolInteractionRepository? = null,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(FortuneUiState())
    val uiState: StateFlow<FortuneUiState> = _uiState.asStateFlow()

    private val sessionStart = now()
    private var hasInteracted = false

    init {
        //inicializa la cache en memoria y sincroniza los datos al arrancar
        viewModelScope.launch {
            fortuneRepository.initCache()
            val remaining = fortuneRepository.getRemainingChances()
            val recents = fortuneRepository.getRecentCookies()
            _uiState.update { it.copy(remainingChances = remaining, recentFortunes = recents) }
            fortuneRepository.syncFavoritesFromRemote()
            fortuneRepository.syncSavedCardsFromRemote()
        }

        //observa la lista de favoritos de room en tiempo real
        viewModelScope.launch {
            fortuneRepository.observeFavorites().collect { favorites ->
                _uiState.update { state ->
                    val updatedActive = state.activeFortune?.let { active ->
                        val isFav = favorites.any { it.id == active.id }
                        active.copy(isFavorite = isFav)
                    }
                    state.copy(favoriteFortunes = favorites, activeFortune = updatedActive)
                }
            }
        }

        //observa las tarjetas guardadas en room y sincronizadas con la nube
        viewModelScope.launch {
            fortuneRepository.observeSavedCards().collect { cards ->
                _uiState.update { it.copy(savedCards = cards) }
            }
        }
    }

    //rompe una galleta con animacion y sonido
    fun crackCookie(cookieIndex: Int) {
        val state = _uiState.value
        if (state.isCracking || state.remainingChances <= 0) {
            if (state.remainingChances <= 0) {
                _uiState.update { it.copy(snackbarMessage = "No chances left for today. Come back tomorrow!") }
            }
            return
        }

        _uiState.update { it.copy(isCracking = true, crackingCookieIndex = cookieIndex) }

        viewModelScope.launch {
            //tiempo de animacion y efecto de sonido del crujido
            delay(600)
            val result = fortuneRepository.crackCookie()
            if (result.isSuccess) {
                val crackData = result.getOrThrow()
                hasInteracted = true
                val recents = fortuneRepository.getRecentCookies()
                _uiState.update {
                    it.copy(
                        isCracking = false,
                        crackingCookieIndex = -1,
                        activeFortune = crackData.fortune,
                        sourceBadge = crackData.sourceMessage,
                        remainingChances = crackData.remainingChances,
                        openedCookies = it.openedCookies + cookieIndex,
                        recentFortunes = recents
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isCracking = false,
                        crackingCookieIndex = -1,
                        snackbarMessage = "No chances left for today"
                    )
                }
            }
        }
    }

    //cambia el estado de favorita de una galleta
    fun toggleFavorite(fortune: FortuneModel) {
        viewModelScope.launch {
            val isNowFav = fortuneRepository.toggleFavorite(fortune)
            val recents = fortuneRepository.getRecentCookies()
            _uiState.update { state ->
                val updatedActive = if (state.activeFortune?.id == fortune.id) {
                    state.activeFortune.copy(isFavorite = isNowFav)
                } else state.activeFortune
                state.copy(
                    activeFortune = updatedActive,
                    recentFortunes = recents,
                    snackbarMessage = if (isNowFav) "Saved to favorites" else "Removed from favorites"
                )
            }
        }
    }

    //selecciona una galleta del carrusel de recientes para verla como tarjeta activa
    fun selectRecentCookie(fortune: FortuneModel) {
        _uiState.update {
            it.copy(
                activeFortune = fortune,
                sourceBadge = "✨ Recent Wisdom"
            )
        }
    }

    //exporta la tarjeta activa a la galeria y la respalda en la nube
    fun exportActiveCard(onUriReady: (Uri) -> Unit) {
        val active = _uiState.value.activeFortune ?: return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val uri = fortuneRepository.saveCardToGallery(active)
            _uiState.update { it.copy(isExporting = false) }
            if (uri != null) {
                onUriReady(uri)
                _uiState.update { it.copy(snackbarMessage = "Card saved to gallery and backed up to cloud") }
            } else {
                _uiState.update { it.copy(snackbarMessage = "Could not save card to gallery") }
            }
        }
    }

    //limpia el mensaje del snackbar
    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    //finaliza la sesion de la herramienta y registra feedback si hubo interaccion
    fun finishSession(): Pair<Boolean, Int> {
        val durationSeconds = ((now() - sessionStart) / 1000).toInt().coerceAtLeast(1)
        if (!hasInteracted) return Pair(false, durationSeconds)
        hasInteracted = false
        val repo = toolInteractionRepository ?: return Pair(true, durationSeconds)
        viewModelScope.launch {
            withContext(NonCancellable) {
                repo.recordCompletion(TOOL_ID, durationSeconds, INTERACTION_TYPE, now())
            }
        }
        return Pair(true, durationSeconds)
    }

    companion object {
        const val TOOL_ID = "fortune_cookie"
        private const val INTERACTION_TYPE = "touch"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                FortuneCookieViewModel(
                    fortuneRepository = app.container.fortuneRepository,
                    toolInteractionRepository = app.container.toolInteractionRepository
                )
            }
        }
    }
}
