package com.moviles.ark.ui.viewmodels

import com.moviles.ark.data.local.AvatarPreferences
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
    val surpriseTool: Tool? = null,
    val navigateToToolId: String? = null,
    val isCheckInCompleted: Boolean = false,
    val errorMessage: String? = null,
    //tarjeta aparte segun el clima de afuera; null sin internet, sin ubicacion o si el clima no sugiere nada (#134)
    val weatherSuggestion: WeatherSuggestion? = null,
    val avatarId: String = AvatarPreferences.DEFAULT_AVATAR_ID
)
