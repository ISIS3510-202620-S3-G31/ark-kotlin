package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.Avatar
import com.moviles.ark.domain.models.MoodCheckInModel
import com.moviles.ark.domain.models.RecommendationReason
import com.moviles.ark.domain.models.ToolModel
import com.moviles.ark.domain.models.ToolCategory
import com.moviles.ark.domain.models.WeatherSuggestion

/**
 * State object representing the UI state of the HomeScreen.
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val tools: List<ToolModel> = emptyList(),
    val filteredTools: List<ToolModel> = emptyList(),
    val selectedCategory: ToolCategory? = null,
    //categorias con alguna herramienta, para los filtros del catalogo
    val availableCategories: List<ToolCategory> = emptyList(),
    //la herramienta que eligio la estrategia de recomendacion; null mientras carga
    val recommendedTool: ToolModel? = null,
    //por que se recomendo; la tarjeta lo explica
    val recommendationReason: RecommendationReason = RecommendationReason.DEFAULT,
    val navigateToToolId: String? = null,
    val isCheckInCompleted: Boolean = false,
    //true una sola vez al abrir el home sin check-in de hoy: la pantalla abre el check-in
    val showCheckInPrompt: Boolean = false,
    //ultimo check-in registrado hoy para la vista de revision no editable
    val todayCheckIn: MoodCheckInModel? = null,
    val errorMessage: String? = null,
    //tarjeta aparte segun el clima de afuera; sin clima trae la sugerencia neutral
    val weatherSuggestion: WeatherSuggestion? = null,
    val avatarId: String = Avatar.DEFAULT_ID,
    //foto de perfil (archivo en el telefono) cuando el usuario eligio mostrarla; null muestra el avatar
    val avatarPhotoPath: String? = null,
    //nombre guardado en el telefono para el saludo del home
    val userName: String = ""
)
