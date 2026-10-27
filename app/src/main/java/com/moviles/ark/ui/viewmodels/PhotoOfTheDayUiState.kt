package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.PhotoDay
import com.moviles.ark.domain.models.PhotoEntryModel

data class PhotoOfTheDayUiState(
    //fecha de hoy ya formateada, por ejemplo "Friday, October 2"
    val todayLabel: String = "",
    //tira de los ultimos 7 dias; el ultimo es hoy y cada dia con foto la trae para el carrusel
    val week: List<PhotoDay> = emptyList(),
    //la foto guardada hoy; solo esta se puede retomar, las de dias pasados no
    val todayPhoto: PhotoEntryModel? = null,
    //dialogo para confirmar que se borra la foto de hoy
    val showDeleteDialog: Boolean = false,
    //foto que entrego la camara y todavia no se guarda
    val pendingPhotoUri: String? = null,
    val caption: String = "",
    //cargando las fotos de la semana
    val isLoading: Boolean = true,
    //guardando o borrando la foto
    val isSaving: Boolean = false,
    //flag para navegar al feedback tras guardar
    val isPhotoSaved: Boolean = false,
    val errorMessage: String? = null,
    //archivo donde la camara esta dejando la foto; se guarda aqui para no perderlo si la pantalla se recrea
    val captureUri: String? = null
)
