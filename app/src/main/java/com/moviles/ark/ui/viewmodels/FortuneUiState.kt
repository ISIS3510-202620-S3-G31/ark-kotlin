package com.moviles.ark.ui.viewmodels

import com.moviles.ark.data.local.entities.SavedFortuneCardEntity
import com.moviles.ark.domain.models.FortuneModel

//estado inmutable de la interfaz de usuario para la galleta de la fortuna
data class FortuneUiState(
    val remainingChances: Int = 5,
    val activeFortune: FortuneModel? = null,
    val isCracking: Boolean = false,
    val crackingCookieIndex: Int = -1,
    val openedCookies: Set<Int> = emptySet(),
    val sourceBadge: String? = null,
    val recentFortunes: List<FortuneModel> = emptyList(),
    val favoriteFortunes: List<FortuneModel> = emptyList(),
    val savedCards: List<SavedFortuneCardEntity> = emptyList(),
    val snackbarMessage: String? = null,
    val isExporting: Boolean = false
)
