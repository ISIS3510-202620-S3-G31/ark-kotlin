package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.Avatar
import com.moviles.ark.domain.models.CheckInModel
import com.moviles.ark.domain.models.Tool
import com.moviles.ark.domain.models.ToolCategory
import com.moviles.ark.domain.models.WeatherSuggestion

/**
 * State object representing the UI state of the HomeScreen.
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val tools: List<Tool> = emptyList(),
    val filteredTools: List<Tool> = emptyList(),
    val selectedCategory: ToolCategory? = null,
    //categorias con alguna herramienta, para los filtros del catalogo
    val availableCategories: List<ToolCategory> = emptyList(),
    //la herramienta que eligio la estrategia de recomendacion; null mientras carga
    val recommendedTool: Tool? = null,
    val navigateToToolId: String? = null,
    val isCheckInCompleted: Boolean = false,
    //true una sola vez al abrir el home sin check-in de hoy: la pantalla abre el check-in
    val showCheckInPrompt: Boolean = false,
    //ultimo check-in registrado hoy para la vista de revision no editable
    val todayCheckIn: CheckInModel? = null,
    val errorMessage: String? = null,
    //tarjeta aparte segun el clima de afuera; null sin internet, sin ubicacion o si el clima no sugiere nada
    val weatherSuggestion: WeatherSuggestion? = null,
    val avatarId: String = Avatar.DEFAULT_ID,
    //nombre guardado en el telefono para el saludo del home
    val userName: String = ""
)
