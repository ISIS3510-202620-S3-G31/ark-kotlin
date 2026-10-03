package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.PhotoDay
import com.moviles.ark.domain.models.PhotoEntry

data class PhotoOfTheDayUiState(
    //fecha de hoy ya formateada, por ejemplo "Friday, October 2"
    val todayLabel: String = "",
    //tira de lunes a domingo
    val week: List<PhotoDay> = emptyList(),
    //la foto guardada hoy; si existe, ya no se toma otra (una foto por dia)
    val todayPhoto: PhotoEntry? = null,
    //foto que entrego la camara y todavia no se guarda
    val pendingPhotoUri: String? = null,
    val caption: String = "",
    //cargando las fotos de la semana
    val isLoading: Boolean = true,
    //guardando la foto nueva
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)
