package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.PhotoDay
import com.moviles.ark.domain.models.PhotoEntryModel

data class PhotoOfTheDayUiState(
    //fecha de hoy ya formateada, por ejemplo "Friday, October 2"
    val todayLabel: String = "",
    //tira de lunes a domingo
    val week: List<PhotoDay> = emptyList(),
    //la foto guardada hoy
    val todayPhoto: PhotoEntryModel? = null,
    //foto de un dia pasado seleccionado en la tira para visualizacion en modo lectura
    val selectedDayPhoto: PhotoEntryModel? = null,
    //nombre del dia pasado seleccionado, por ejemplo "Monday"
    val selectedDayName: String? = null,
    //indica si el usuario selecciono un dia pasado de la tira semanal
    val isPastDaySelected: Boolean = false,
    //controla la visibilidad del dialogo modal de confirmacion para eliminar la foto de hoy
    val showDeleteDialog: Boolean = false,
    //foto que entrego la camara y todavia no se guarda
    val pendingPhotoUri: String? = null,
    val caption: String = "",
    //cargando las fotos de la semana
    val isLoading: Boolean = true,
    //guardando o eliminando la foto
    val isSaving: Boolean = false,
    //flag para navegar al feedback tras guardar
    val isPhotoSaved: Boolean = false,
    val errorMessage: String? = null
)
