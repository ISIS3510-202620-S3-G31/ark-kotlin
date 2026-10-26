package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.PhotoDay
import com.moviles.ark.domain.models.PhotoEntryModel

data class PhotoOfTheDayUiState(
    //fecha de hoy ya formateada, por ejemplo "Friday, October 2"
    val todayLabel: String = "",
    //tira de 7 dias terminando en hoy (#152)
    val week: List<PhotoDay> = emptyList(),
    //la foto guardada hoy; si existe, ya no se toma otra (una foto por dia)
    val todayPhoto: PhotoEntryModel? = null,
    //foto que entrego la camara y todavia no se guarda
    val pendingPhotoUri: String? = null,
    val caption: String = "",
    //cargando las fotos de la semana
    val isLoading: Boolean = true,
    //guardando la foto nueva
    val isSaving: Boolean = false,
    //flag para navegar al feedback tras guardar
    val isPhotoSaved: Boolean = false,
    val errorMessage: String? = null,
    //dia seleccionado en la tira de circulos (#152)
    val selectedDay: PhotoDay? = null,
    //foto mostrada en el visor principal: de hoy o de un dia previo (#152)
    val displayedPhoto: PhotoEntryModel? = null
)
