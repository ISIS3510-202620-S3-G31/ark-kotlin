package com.moviles.ark.ui.viewmodels

import com.moviles.ark.data.local.AvatarPreferences
import com.moviles.ark.domain.models.Tool
import com.moviles.ark.domain.models.ToolCategory

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
    val avatarId: String = AvatarPreferences.DEFAULT_AVATAR_ID
)
