package com.moviles.ark.ui.viewmodels

import android.net.Uri
import com.moviles.ark.domain.models.FortuneCardModel
import com.moviles.ark.domain.models.FortuneModel
import com.moviles.ark.domain.models.TableFortune

//galletas que se pueden abrir por dia, igual que en FortunePreferencesManager
const val DAILY_FORTUNE_CHANCES = 5

//momentos de la sesion, elegir una galleta, verla abrirse y leer el papelito
enum class FortunePhase { CHOOSING, OPENING, REVEALED }

//estado de la pantalla de la galleta y de la coleccion
data class FortuneUiState(
    val phase: FortunePhase = FortunePhase.CHOOSING,
    val table: List<TableFortune> = emptyList(),
    val chosenIndex: Int = -1,
    val opened: TableFortune? = null,
    //-1 mientras se lee de sharedpreferences
    val remainingChances: Int = -1,
    //aviso solo cuando no hay internet, null si hay red
    val offlineNotice: String? = null,
    val previewFortune: FortuneModel? = null,
    val recentFortunes: List<FortuneModel> = emptyList(),
    val favoriteFortunes: List<FortuneModel> = emptyList(),
    val savedCards: List<FortuneCardModel> = emptyList(),
    //si hay internet, para explicar por que no salen las recientes
    val isOnline: Boolean = true,
    val isSharing: Boolean = false,
    val shareUri: Uri? = null,
    val snackbarMessage: String? = null
)
